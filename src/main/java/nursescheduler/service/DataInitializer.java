package nursescheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import nursescheduler.model.Appointment;
import nursescheduler.model.Nurse;
import nursescheduler.model.Patient;
import nursescheduler.repository.AppointmentRepository;
import nursescheduler.repository.NurseRepository;
import nursescheduler.repository.PatientRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service to initialize data in the database from JSON files
 */
@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private ResourceLoader resourceLoader;
    
    @Autowired
    private NurseRepository nurseRepository;
    
    @Autowired
    private PatientRepository patientRepository;
    
    @Autowired
    private AppointmentRepository appointmentRepository;
    
    @Autowired
    private PhotonGeocodingService geocodingService;
    
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void run(String... args) {
        // Only initialize if the database is empty
        if (nurseRepository.count() == 0 && patientRepository.count() == 0) {
            System.out.println("Database is empty. Loading data from JSON files...");
            loadDataFromJson();
        } else {
            System.out.println("Database already contains data. Skipping initialization.");
            
            // Log count of existing records
            System.out.println("Existing records: " + nurseRepository.count() + " nurses, " 
                + patientRepository.count() + " patients, " 
                + appointmentRepository.count() + " appointments");
        }
    }
    
    private void loadDataFromJson() {
        try {
            System.out.println("Loading data from JSON files...");
            
            // First, load appointments to identify active nurses
            List<Map<String, Object>> appointmentsList = loadAppointmentsFromJson();
            if (appointmentsList == null) {
                return;
            }
            
            // Create a map of practitioner IDs to appointment counts
            Map<String, Integer> nurseAppointmentCounts = new HashMap<>();
            for (Map<String, Object> appointmentData : appointmentsList) {
                String practitionerId = (String) appointmentData.get("practitionerId");
                nurseAppointmentCounts.put(practitionerId, nurseAppointmentCounts.getOrDefault(practitionerId, 0) + 1);
            }
            
            System.out.println("Found " + nurseAppointmentCounts.size() + " nurses with appointments");
            
            // Load workers (nurses) JSON
            Resource workersResource = resourceLoader.getResource("classpath:static/JSON/workers.json");
            if (!workersResource.exists()) {
                System.err.println("Workers JSON file not found. Cannot initialize data.");
                return;
            }
            
            // Fix the type safety issue with proper typing
            Map<String, Object> workersData = mapper.readValue(
                workersResource.getInputStream(), 
                new TypeReference<Map<String, Object>>() {}
            );
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> workers = (List<Map<String, Object>>) workersData.get("workers");
            System.out.println("Found " + workers.size() + " workers in JSON file");
            
            // Convert and save nurses
            List<Nurse> nurses = new ArrayList<>();
            int fieldNurses = 0;
            int officeStaff = 0;
            int geocodedNurses = 0;
            int missingCoordinates = 0;
            
            for (Map<String, Object> worker : workers) {
                Nurse nurse = new Nurse();
                
                // Set nurse ID (use workerId as a string)
                String workerId = (String) worker.get("workerId");
                nurse.setId(Long.parseLong(Math.abs(workerId.hashCode()) + "")); // Convert string ID to a numeric ID
                
                // Set nurse name (firstname + lastname)
                String firstName = (String) worker.get("firstName");
                String lastName = (String) worker.get("lastName");
                nurse.setName(firstName + " " + lastName);
                
                // Determine if this is a field nurse or office staff
                boolean hasAppointments = nurseAppointmentCounts.containsKey(workerId) && 
                                          nurseAppointmentCounts.get(workerId) > 0;
                
                // Set field nurse flag (we've now added this property to Nurse model)
                nurse.setFieldStaff(hasAppointments);
                
                if (hasAppointments) {
                    fieldNurses++;
                } else {
                    officeStaff++;
                }
                
                // Get address info if available
                String nurseAddress = "";
                @SuppressWarnings("unchecked")
                Map<String, Object> address = (Map<String, Object>) worker.get("address");
                if (address != null) {
                    String street = (String) address.get("street");
                    String city = (String) address.get("city");
                    String state = (String) address.get("state");
                    String zip = (String) address.get("zip");
                    nurseAddress = street + ", " + city + ", " + state + " " + zip;
                }
                
                // Check for coordinates
                boolean hasCoordinates = false;
                @SuppressWarnings("unchecked")
                Map<String, Object> coordinates = (Map<String, Object>) worker.get("coordinates");
                if (coordinates != null && coordinates.containsKey("latitude") && coordinates.containsKey("longitude")) {
                    Double lat = (Double) coordinates.get("latitude");
                    Double lng = (Double) coordinates.get("longitude");
                    
                    // Only use coordinates if they are not zero or null
                    if (lat != null && lng != null && (Math.abs(lat) > 0.001 || Math.abs(lng) > 0.001)) {
                        nurse.setLatitude(lat);
                        nurse.setLongitude(lng);
                        hasCoordinates = true;
                        System.out.println("Nurse " + nurse.getName() + " has coordinates: " + lat + ", " + lng);
                    }
                }
                
                // Only geocode field nurses with no coordinates but an address
                if (hasAppointments && !hasCoordinates && !nurseAddress.isEmpty()) {
                    System.out.println("Geocoding address for field nurse " + nurse.getName() + ": " + nurseAddress);
                    hasCoordinates = tryGeocodeAddress(nurse, nurseAddress);
                    if (hasCoordinates) {
                        geocodedNurses++;
                    } else {
                        missingCoordinates++;
                    }
                } else if (hasAppointments && !hasCoordinates) {
                    // Field nurse with no address and no coordinates
                    System.out.println("Field nurse " + nurse.getName() + " has no address or coordinates. Cannot determine location.");
                    missingCoordinates++;
                }
                
                nurses.add(nurse);
            }
            
            nurseRepository.saveAll(nurses);
            System.out.println("Loaded " + nurses.size() + " nurses:");
            System.out.println("  - Field nurses: " + fieldNurses);
            System.out.println("  - Office staff: " + officeStaff);
            System.out.println("  - Geocoded: " + geocodedNurses);
            System.out.println("  - Missing coordinates: " + missingCoordinates);
            
            // Load patients JSON
            List<Patient> patients = loadPatientsFromJson();
            if (patients == null) {
                return;
            }
            
            // Now process appointments and link to patients and nurses
            processAppointments(appointmentsList, patients, nurses);
            
        } catch (Exception e) {
            System.err.println("Error loading data from JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private List<Map<String, Object>> loadAppointmentsFromJson() {
        try {
            // Load appointments JSON
            Resource appointmentsResource = resourceLoader.getResource("classpath:static/JSON/appointments.json");
            if (!appointmentsResource.exists()) {
                System.err.println("Appointments JSON file not found. Cannot initialize appointment data.");
                return null;
            }
            
            // Use TypeReference to fix type safety warning
            Map<String, Object> appointmentsData = mapper.readValue(
                appointmentsResource.getInputStream(),
                new TypeReference<Map<String, Object>>() {}
            );
            
            @SuppressWarnings("unchecked")
            Map<String, Object> appointmentsContainer = (Map<String, Object>) appointmentsData.get("appointments");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> appointmentsList = (List<Map<String, Object>>) appointmentsContainer.get("all");
            
            System.out.println("Found " + appointmentsList.size() + " appointments in JSON file");
            
            return appointmentsList;
        } catch (Exception e) {
            System.err.println("Error loading appointments: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    private List<Patient> loadPatientsFromJson() {
        try {
            // Load patients JSON
            Resource patientsResource = resourceLoader.getResource("classpath:static/JSON/patients.json");
            if (!patientsResource.exists()) {
                System.err.println("Patients JSON file not found. Cannot initialize data.");
                return null;
            }
            
            // Use TypeReference to fix type safety warning
            Map<String, Object> patientsData = mapper.readValue(
                patientsResource.getInputStream(),
                new TypeReference<Map<String, Object>>() {}
            );
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> patientsList = (List<Map<String, Object>>) patientsData.get("patients");
            
            System.out.println("Found " + patientsList.size() + " patients in JSON file");
            
            // Convert and save patients
            List<Patient> patients = new ArrayList<>();
            int geocodedPatients = 0;
            int patientsMissingCoordinates = 0;
            
            for (Map<String, Object> patientData : patientsList) {
                Patient patient = new Patient();
                
                // Set patient ID
                String patientId = (String) patientData.get("patientId");
                patient.setId(Long.parseLong(Math.abs(patientId.hashCode()) + "")); // Convert string ID to a numeric ID
                
                // Set patient name
                String firstName = (String) patientData.get("firstName");
                String lastName = (String) patientData.get("lastName");
                patient.setName(firstName + " " + lastName);
                
                // Set address components
                String street = (String) patientData.get("street");
                String city = (String) patientData.get("city");
                String state = (String) patientData.get("state");
                String zip = (String) patientData.get("zip");
                
                // Set full address for display and routing
                String fullAddress = street + ", " + city + ", " + state + " " + zip;
                patient.setAddress(fullAddress);
                
                // Check for coordinates
                boolean hasCoordinates = false;
                @SuppressWarnings("unchecked")
                Map<String, Object> coordinates = (Map<String, Object>) patientData.get("coordinates");
                if (coordinates != null && coordinates.containsKey("latitude") && coordinates.containsKey("longitude")) {
                    Double lat = (Double) coordinates.get("latitude");
                    Double lng = (Double) coordinates.get("longitude");
                    
                    // Only use coordinates if they are not zero or null
                    if (lat != null && lng != null && (Math.abs(lat) > 0.001 || Math.abs(lng) > 0.001)) {
                        patient.setLatitude(lat);
                        patient.setLongitude(lng);
                        hasCoordinates = true;
                        System.out.println("Patient " + patient.getName() + " has coordinates: " + lat + ", " + lng);
                    }
                }
                
                // If no coordinates but has address, geocode with multiple fallback strategies
                if (!hasCoordinates && !fullAddress.isEmpty()) {
                    System.out.println("Geocoding address for patient " + patient.getName() + ": " + fullAddress);
                    hasCoordinates = tryGeocodePatientAddress(patient, street, city, state, zip);
                    if (hasCoordinates) {
                        geocodedPatients++;
                    } else {
                        patientsMissingCoordinates++;
                    }
                } else if (!hasCoordinates) {
                    // No address and no coordinates
                    System.out.println("No coordinates for patient " + patient.getName() + ". Cannot determine location.");
                    patientsMissingCoordinates++;
                }
                
                // Set default time and duration
                patient.setTime("09:00 AM"); // Will be updated by appointments
                patient.setDuration(30);     // Will be updated by appointments
                
                patients.add(patient);
            }
            
            patientRepository.saveAll(patients);
            System.out.println("Loaded " + patients.size() + " patients (" + geocodedPatients + " geocoded, " + patientsMissingCoordinates + " missing coordinates)");
            return patients;
        } catch (Exception e) {
            System.err.println("Error loading patients: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    private void processAppointments(List<Map<String, Object>> appointmentsList, List<Patient> patients, List<Nurse> nurses) {
        try {
            // DateTimeFormatter for parsing appointment dates
            DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
            
            // Convert and save appointments
            List<Appointment> appointments = new ArrayList<>();
            int parsedAppointments = 0;
            int failedAppointments = 0;
            
            // Create a map of patient IDs to patients for faster lookup
            Map<Long, Patient> patientMap = patients.stream()
                .collect(Collectors.toMap(Patient::getId, patient -> patient));
            
            for (Map<String, Object> appointmentData : appointmentsList) {
                try {
                    Appointment appointment = new Appointment();
                    
                    // Set appointment ID
                    appointment.setAppointmentId((String) appointmentData.get("appointmentId"));
                    appointment.setPatientId((String) appointmentData.get("patientId"));
                    appointment.setPractitionerId((String) appointmentData.get("practitionerId"));
                    
                    // Parse and set appointment date/time
                    String dateTimeStr = (String) appointmentData.get("appointmentDate");
                    LocalDateTime dateTime = LocalDateTime.parse(dateTimeStr, formatter);
                    appointment.setAppointmentDate(dateTime);
                    
                    // Set visit type and service code
                    appointment.setVisitType((String) appointmentData.get("visitType"));
                    appointment.setServiceCode((String) appointmentData.get("serviceCode"));
                    
                    appointments.add(appointment);
                    parsedAppointments++;
                    
                    // Update corresponding patient's time based on appointment
                    String patientId = (String) appointmentData.get("patientId");
                    Long numericPatientId = Long.parseLong(Math.abs(patientId.hashCode()) + "");
                    Patient patient = patientMap.get(numericPatientId);
                    
                    if (patient != null) {
                        // Extract time from the appointment and format for display
                        String time = dateTime.toLocalTime().toString();
                        patient.setTime(time);
                        
                        // Set duration based on visit type
                        String visitType = appointment.getVisitType();
                        if (visitType != null && visitType.contains("HOSPICE")) {
                            patient.setDuration(60); // Longer for hospice visits
                        } else {
                            patient.setDuration(30); // Standard duration
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error processing appointment: " + e.getMessage());
                    failedAppointments++;
                }
            }
            
            // Save appointments
            appointmentRepository.saveAll(appointments);
            
            // Update patients with appointment times
            patientRepository.saveAll(patients);
            
            System.out.println("Loaded " + appointments.size() + " appointments (" + parsedAppointments + " successful, " + failedAppointments + " failed)");
            System.out.println("Database initialization complete!");
        } catch (Exception e) {
            System.err.println("Error processing appointments: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Try to geocode a nurse's address
     * @return true if geocoding was successful, false otherwise
     */
    private boolean tryGeocodeAddress(Nurse nurse, String address) {
        try {
            // Try geocoding the full address
            PhotonGeocodingService.GeocodingResult result = geocodingService.geocodeAddress(address);
            if (result != null) {
                nurse.setLatitude(result.getLatitude());
                nurse.setLongitude(result.getLongitude());
                System.out.println("Successfully geocoded to: " + result.getLatitude() + ", " + result.getLongitude());
                return true;
            }
            
            System.err.println("Failed to geocode address: " + address);
            return false;
        } catch (Exception e) {
            System.err.println("Error geocoding address: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Try multiple geocoding strategies for a patient address
     * @return true if any geocoding strategy was successful, false otherwise
     */
    private boolean tryGeocodePatientAddress(Patient patient, String street, String city, String state, String zip) {
        try {
            // Strategy 1: Try full address
            String fullAddress = street + ", " + city + ", " + state + " " + zip;
            PhotonGeocodingService.GeocodingResult result = geocodingService.geocodeAddress(fullAddress);
            
            if (result != null) {
                patient.setLatitude(result.getLatitude());
                patient.setLongitude(result.getLongitude());
                System.out.println("Successfully geocoded full address to: " + result.getLatitude() + ", " + result.getLongitude());
                return true;
            }
            
            // Strategy 2: Try address without street number (extract street name only)
            String streetName = street;
            if (street != null && street.matches("\\d+.*")) {
                streetName = street.replaceAll("^\\d+\\s*", ""); // Remove leading numbers
                String addressWithoutNumber = streetName + ", " + city + ", " + state + " " + zip;
                result = geocodingService.geocodeAddress(addressWithoutNumber);
                
                if (result != null) {
                    patient.setLatitude(result.getLatitude());
                    patient.setLongitude(result.getLongitude());
                    System.out.println("Successfully geocoded street name to: " + result.getLatitude() + ", " + result.getLongitude());
                    return true;
                }
            }
            
            // Strategy 3: Try just city and state
            String cityState = city + ", " + state;
            result = geocodingService.geocodeAddress(cityState);
            
            if (result != null) {
                patient.setLatitude(result.getLatitude());
                patient.setLongitude(result.getLongitude());
                System.out.println("Successfully geocoded city/state to: " + result.getLatitude() + ", " + result.getLongitude());
                return true;
            }
            
            // All geocoding strategies failed
            System.err.println("All geocoding strategies failed for address: " + fullAddress);
            return false;
        } catch (Exception e) {
            System.err.println("Error in geocoding patient address: " + e.getMessage());
            return false;
        }
    }
}