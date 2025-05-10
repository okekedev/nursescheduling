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
import nursescheduler.model.NurseSchedule;
import nursescheduler.model.Patient;
import nursescheduler.repository.AppointmentRepository;
import nursescheduler.repository.NurseRepository;
import nursescheduler.repository.NurseScheduleRepository;
import nursescheduler.repository.PatientRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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
    private NurseScheduleRepository nurseScheduleRepository;
    
    @Autowired
    private NurseScheduleService nurseScheduleService;
    
    @Autowired
    private PhotonGeocodingService geocodingService;
    
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void run(String... args) {
        try {
            // Only initialize if the database is empty
            if (nurseRepository.count() == 0 && patientRepository.count() == 0) {
                System.out.println("Database is empty. Loading data from JSON files...");
                loadDataFromJson();
                
                // Generate schedules for all nurses after data is loaded
                generateSchedulesForNurses();
            } else {
                System.out.println("Database already contains data. Skipping initialization.");
                
                // Log count of existing records
                System.out.println("Existing records: " + nurseRepository.count() + " nurses, " 
                    + patientRepository.count() + " patients, " 
                    + appointmentRepository.count() + " appointments, "
                    + nurseScheduleRepository.count() + " schedules");
            }
        } catch (Exception e) {
            System.err.println("Error in data initialization: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // New method to generate schedules
    private void generateSchedulesForNurses() {
        System.out.println("Generating schedules for all nurses...");
        LocalDate today = LocalDate.now();
        
        List<Nurse> fieldNurses = nurseRepository.findAll().stream()
            .filter(nurse -> nurse.getLatitude() != null && nurse.getLongitude() != null)
            .collect(Collectors.toList());
            
        System.out.println("Found " + fieldNurses.size() + " field nurses with coordinates");
        
        for (Nurse nurse : fieldNurses) {
            try {
                // Get appointments for today
                String nurseId = String.valueOf(nurse.getId());
                
                // Check if nurse has any appointments
                List<Appointment> appointments = appointmentRepository.findByPractitionerId(nurseId);
                
                if (!appointments.isEmpty()) {
                    System.out.println("Nurse " + nurse.getName() + " has " + appointments.size() + " appointments, generating schedule...");
                    NurseSchedule schedule = nurseScheduleService.generateSchedule(nurseId, today);
                    System.out.println("Generated schedule for " + nurse.getName() + " with ID " + schedule.getId() + 
                                       " and " + schedule.getPatientVisitOrder().size() + " patient visits");
                } else {
                    System.out.println("Nurse " + nurse.getName() + " has no appointments for today");
                }
            } catch (Exception e) {
                System.err.println("Error generating schedule for nurse " + nurse.getName() + ": " + e.getMessage());
            }
        }
        
        System.out.println("Schedule generation complete. Total schedules: " + nurseScheduleRepository.count());
    }
    
    private void loadDataFromJson() {
        try {
            System.out.println("Loading data from JSON files...");
            
            // Load workers (nurses) JSON
            List<Nurse> nurses = loadNursesFromJson();
            if (nurses == null || nurses.isEmpty()) {
                System.err.println("Error: No nurses loaded from JSON");
                return;
            }
            
            // Load patients JSON
            List<Patient> patients = loadPatientsFromJson();
            if (patients == null || patients.isEmpty()) {
                System.err.println("Error: No patients loaded from JSON");
                return;
            }
            
            // Load appointments JSON
            List<Appointment> appointments = loadAppointmentsFromJson();
            if (appointments == null || appointments.isEmpty()) {
                System.err.println("Error: No appointments loaded from JSON");
                return;
            }
            
            // Log loaded data
            System.out.println("Successfully loaded: " + nurses.size() + " nurses, " + 
                               patients.size() + " patients, " + 
                               appointments.size() + " appointments");
        } catch (Exception e) {
            System.err.println("Error loading data from JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private List<Nurse> loadNursesFromJson() {
        try {
            Resource workersResource = resourceLoader.getResource("classpath:static/JSON/workers.json");
            if (!workersResource.exists()) {
                System.err.println("Workers JSON file not found at classpath:static/JSON/workers.json");
                return Collections.emptyList();
            }
            
            // Log file path and existence check
            System.out.println("Loading workers from: " + workersResource.getURL());
            
            // Parse the JSON
            Map<String, Object> workersData = mapper.readValue(
                workersResource.getInputStream(), 
                new TypeReference<Map<String, Object>>() {}
            );
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> workers = (List<Map<String, Object>>) workersData.get("workers");
            System.out.println("Found " + workers.size() + " workers in JSON file");
            
            // Convert JSON to Nurse entities
            List<Nurse> nurses = new ArrayList<>();
            int fieldNurses = 0;
            int officeStaff = 0;
            int geocodedNurses = 0;
            
            for (Map<String, Object> worker : workers) {
                Nurse nurse = new Nurse();
                
                // Set nurse ID (use workerId as a string)
                String workerId = (String) worker.get("workerId");
                nurse.setId(Long.parseLong(Math.abs(workerId.hashCode()) + ""));
                
                // Set nurse name
                String firstName = (String) worker.get("firstName");
                String lastName = (String) worker.get("lastName");
                nurse.setName(firstName + " " + lastName);
                
                // All nurses should be considered field staff for this exercise
                nurse.setFieldStaff(true);
                fieldNurses++;
                
                // Get address info
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
                    Double lat = convertToDouble(coordinates.get("latitude"));
                    Double lng = convertToDouble(coordinates.get("longitude"));
                    
                    // Only use coordinates if they are not zero or null
                    if (lat != null && lng != null && (Math.abs(lat) > 0.001 || Math.abs(lng) > 0.001)) {
                        nurse.setLatitude(lat);
                        nurse.setLongitude(lng);
                        hasCoordinates = true;
                        System.out.println("Nurse " + nurse.getName() + " has coordinates: " + lat + ", " + lng);
                    }
                }
                
                // Geocode address if no coordinates
                if (!hasCoordinates && !nurseAddress.isEmpty()) {
                    System.out.println("Geocoding address for nurse " + nurse.getName() + ": " + nurseAddress);
                    hasCoordinates = tryGeocodeAddress(nurse, nurseAddress);
                    if (hasCoordinates) {
                        geocodedNurses++;
                    }
                }
                
                // If still no coordinates, set some default values (for testing only)
                if (!hasCoordinates) {
                    // Default to Wichita Falls, TX area with some random offset
                    double randomLat = 33.9137 + (Math.random() - 0.5) * 0.1;
                    double randomLng = -98.4934 + (Math.random() - 0.5) * 0.1;
                    nurse.setLatitude(randomLat);
                    nurse.setLongitude(randomLng);
                    System.out.println("Setting default coordinates for nurse " + nurse.getName() + ": " + randomLat + ", " + randomLng);
                }
                
                nurses.add(nurse);
            }
            
            // Save all nurses
            nurseRepository.saveAll(nurses);
            
            System.out.println("Loaded " + nurses.size() + " nurses:");
            System.out.println("  - Field nurses: " + fieldNurses);
            System.out.println("  - Office staff: " + officeStaff);
            System.out.println("  - Geocoded: " + geocodedNurses);
            
            return nurses;
        } catch (Exception e) {
            System.err.println("Error loading nurses from JSON: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
    
    private List<Patient> loadPatientsFromJson() {
        try {
            Resource patientsResource = resourceLoader.getResource("classpath:static/JSON/patients.json");
            if (!patientsResource.exists()) {
                System.err.println("Patients JSON file not found at classpath:static/JSON/patients.json");
                return Collections.emptyList();
            }
            
            // Log file path
            System.out.println("Loading patients from: " + patientsResource.getURL());
            
            // Parse the JSON
            Map<String, Object> patientsData = mapper.readValue(
                patientsResource.getInputStream(),
                new TypeReference<Map<String, Object>>() {}
            );
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> patientsList = (List<Map<String, Object>>) patientsData.get("patients");
            
            System.out.println("Found " + patientsList.size() + " patients in JSON file");
            
            // Convert JSON to Patient entities
            List<Patient> patients = new ArrayList<>();
            int geocodedPatients = 0;
            int patientsMissingCoordinates = 0;
            
            for (Map<String, Object> patientData : patientsList) {
                Patient patient = new Patient();
                
                // Set patient ID
                String patientId = (String) patientData.get("patientId");
                patient.setId(Long.parseLong(Math.abs(patientId.hashCode()) + ""));
                
                // Set patient name
                String firstName = (String) patientData.get("firstName");
                String lastName = (String) patientData.get("lastName");
                patient.setName(firstName + " " + lastName);
                
                // Set address components
                String street = (String) patientData.get("street");
                String city = (String) patientData.get("city");
                String state = (String) patientData.get("state");
                String zip = (String) patientData.get("zip");
                patient.setCity(city);
                patient.setState(state);
                patient.setZip(zip);
                
                // Set full address for display and routing
                String fullAddress = street + ", " + city + ", " + state + " " + zip;
                patient.setAddress(fullAddress);
                
                // Check for coordinates
                boolean hasCoordinates = false;
                @SuppressWarnings("unchecked")
                Map<String, Object> coordinates = (Map<String, Object>) patientData.get("coordinates");
                if (coordinates != null && coordinates.containsKey("latitude") && coordinates.containsKey("longitude")) {
                    Double lat = convertToDouble(coordinates.get("latitude"));
                    Double lng = convertToDouble(coordinates.get("longitude"));
                    
                    // Only use coordinates if they are not zero or null
                    if (lat != null && lng != null && (Math.abs(lat) > 0.001 || Math.abs(lng) > 0.001)) {
                        patient.setLatitude(lat);
                        patient.setLongitude(lng);
                        hasCoordinates = true;
                        System.out.println("Patient " + patient.getName() + " has coordinates: " + lat + ", " + lng);
                    }
                }
                
                // Geocode address if no coordinates
                if (!hasCoordinates && !fullAddress.isEmpty()) {
                    System.out.println("Geocoding address for patient " + patient.getName() + ": " + fullAddress);
                    hasCoordinates = tryGeocodePatientAddress(patient, street, city, state, zip);
                    if (hasCoordinates) {
                        geocodedPatients++;
                    } else {
                        patientsMissingCoordinates++;
                    }
                }
                
                // If still no coordinates, set some default values (for testing only)
                if (!hasCoordinates) {
                    // Default to Wichita Falls, TX area with some random offset
                    double randomLat = 33.9137 + (Math.random() - 0.5) * 0.1;
                    double randomLng = -98.4934 + (Math.random() - 0.5) * 0.1;
                    patient.setLatitude(randomLat);
                    patient.setLongitude(randomLng);
                    System.out.println("Setting default coordinates for patient " + patient.getName() + ": " + randomLat + ", " + randomLng);
                }
                
                // Set default time and duration (will be updated by appointments)
                patient.setTime("09:00 AM");
                patient.setDuration(30);
                
                patients.add(patient);
            }
            
            // Save all patients
            patientRepository.saveAll(patients);
            
            System.out.println("Loaded " + patients.size() + " patients (" + geocodedPatients + " geocoded, " + patientsMissingCoordinates + " with default coordinates)");
            return patients;
        } catch (Exception e) {
            System.err.println("Error loading patients from JSON: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
    
    private List<Appointment> loadAppointmentsFromJson() {
        try {
            Resource appointmentsResource = resourceLoader.getResource("classpath:static/JSON/appointments.json");
            if (!appointmentsResource.exists()) {
                System.err.println("Appointments JSON file not found at classpath:static/JSON/appointments.json");
                return Collections.emptyList();
            }
            
            // Log file path
            System.out.println("Loading appointments from: " + appointmentsResource.getURL());
            
            // Parse the JSON
            Map<String, Object> appointmentsData = mapper.readValue(
                appointmentsResource.getInputStream(),
                new TypeReference<Map<String, Object>>() {}
            );
            
            @SuppressWarnings("unchecked")
            Map<String, Object> appointmentsContainer = (Map<String, Object>) appointmentsData.get("appointments");
            
            if (appointmentsContainer == null) {
                System.err.println("Invalid appointments JSON structure: missing 'appointments' key");
                return Collections.emptyList();
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> appointmentsList = (List<Map<String, Object>>) appointmentsContainer.get("all");
            
            if (appointmentsList == null) {
                System.err.println("Invalid appointments JSON structure: missing 'appointments.all' key");
                return Collections.emptyList();
            }
            
            System.out.println("Found " + appointmentsList.size() + " appointments in JSON file");
            
            // Convert JSON to Appointment entities
            List<Appointment> appointments = new ArrayList<>();
            Map<Long, Patient> patientMap = new HashMap<>();
            
            // Create a map of patient IDs to patients for easy lookup
            for (Patient patient : patientRepository.findAll()) {
                patientMap.put(patient.getId(), patient);
            }
            
            DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
            int successCount = 0;
            int errorCount = 0;
            
            // Set all appointments to today for testing
            LocalDate today = LocalDate.now();
            LocalDateTime startOfDay = today.atStartOfDay();
            
            // Process each appointment
            for (Map<String, Object> appointmentData : appointmentsList) {
                try {
                    Appointment appointment = new Appointment();
                    
                    // Set appointment ID
                    appointment.setAppointmentId((String) appointmentData.get("appointmentId"));
                    
                    // Set patient and practitioner IDs
                    String patientId = (String) appointmentData.get("patientId");
                    String practitionerId = (String) appointmentData.get("practitionerId");
                    appointment.setPatientId(patientId);
                    appointment.setPractitionerId(practitionerId);
                    
                    // Parse and set appointment date
                    // NOTE: For testing, we're setting all appointments to today at different times
                    String dateTimeStr = (String) appointmentData.get("appointmentDate");
                    LocalDateTime originalDateTime;
                    try {
                        originalDateTime = LocalDateTime.parse(dateTimeStr, formatter);
                    } catch (Exception e) {
                        // If parsing fails, use a default time
                       originalDateTime = startOfDay.plusHours(8).plusMinutes((long)(Math.random() * 480)); // 8 AM to 4 PM
                    }
                    
                    // Use the original time but today's date
                    LocalDateTime appointmentDateTime = startOfDay
                        .plusHours(originalDateTime.getHour())
                        .plusMinutes(originalDateTime.getMinute());
                    
                    appointment.setAppointmentDate(appointmentDateTime);
                    
                    // Set visit type and service code
                    appointment.setVisitType((String) appointmentData.get("visitType"));
                    appointment.setServiceCode((String) appointmentData.get("serviceCode"));
                    
                    appointments.add(appointment);
                    
                    // Update patient visit time based on appointment
                    Long numericPatientId = Long.parseLong(Math.abs(patientId.hashCode()) + "");
                    Patient patient = patientMap.get(numericPatientId);
                    
                    if (patient != null) {
                        // Format time for display (HH:MM AM/PM)
                        String timeStr = appointmentDateTime.toLocalTime()
                            .format(DateTimeFormatter.ofPattern("hh:mm a"));
                        patient.setTime(timeStr);
                        
                        // Set duration based on visit type
                        String visitType = appointment.getVisitType();
                        if (visitType != null && visitType.contains("HOSPICE")) {
                            patient.setDuration(60); // Longer for hospice visits
                        } else {
                            patient.setDuration(30); // Standard duration
                        }
                    }
                    
                    successCount++;
                } catch (Exception e) {
                    System.err.println("Error processing appointment: " + e.getMessage());
                    errorCount++;
                }
            }
            
            // Save all appointments
            appointmentRepository.saveAll(appointments);
            
            // Update patients with appointment times
            patientRepository.saveAll(patientMap.values());
            
            System.out.println("Loaded " + appointments.size() + " appointments (" + successCount + " successful, " + errorCount + " failed)");
            return appointments;
        } catch (Exception e) {
            System.err.println("Error loading appointments from JSON: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
    
    // Helper for converting JSON values to Double
    private Double convertToDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
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