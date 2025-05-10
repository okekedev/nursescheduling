package nursescheduler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing nurse schedules
 * Includes logic for generating optimal routes and creating schedule entities
 */
@Service
public class NurseScheduleService {

    @Autowired
    private NurseRepository nurseRepository;
    
    @Autowired
    private PatientRepository patientRepository;
    
    @Autowired
    private AppointmentRepository appointmentRepository;
    
    @Autowired
    private NurseScheduleRepository nurseScheduleRepository;
    
    @Autowired
    private GraphHopperService graphHopperService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Generate or retrieve a schedule for a nurse on a specific date
     */
    @Transactional
    public NurseSchedule getOrGenerateSchedule(String nurseId, LocalDate date) {
        System.out.println("Getting or generating schedule for nurse " + nurseId + " on " + date);
        
        // Check if schedule already exists
        NurseSchedule existingSchedule = nurseScheduleRepository.findByNurseIdAndScheduleDate(nurseId, date);
        if (existingSchedule != null) {
            System.out.println("Found existing schedule with ID " + existingSchedule.getId());
            return existingSchedule;
        }
        
        System.out.println("No existing schedule found, generating new schedule");
        // Generate a new schedule
        return generateSchedule(nurseId, date);
    }
    
    /**
     * Generate a new schedule for a nurse on a specific date
     */
    @Transactional
    public NurseSchedule generateSchedule(String nurseId, LocalDate date) {
        System.out.println("Generating new schedule for nurse " + nurseId + " on " + date);
        
        // Find the nurse
        Nurse nurse = null;
        for (Nurse n : nurseRepository.findAll()) {
            if (String.valueOf(n.getId()).equals(nurseId)) {
                nurse = n;
                break;
            }
        }
        
        if (nurse == null) {
            System.err.println("Nurse not found with ID: " + nurseId);
            throw new RuntimeException("Nurse not found with ID: " + nurseId);
        }
        
        System.out.println("Found nurse: " + nurse.getName() + " (ID: " + nurse.getId() + ")");
        
        // Check if the nurse has coordinates
        if (nurse.getLatitude() == null || nurse.getLongitude() == null) {
            System.err.println("Nurse has no coordinates. Setting default coordinates.");
            // Set default coordinates for Wichita Falls
            nurse.setLatitude(33.9137);
            nurse.setLongitude(-98.4934);
            nurseRepository.save(nurse);
        }
        
        // Find appointments for the nurse on the specified date
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
        
        System.out.println("Looking for appointments between " + startOfDay + " and " + endOfDay);
        System.out.println("Nurse ID for appointment lookup: " + nurseId);
        
        List<Appointment> appointments = appointmentRepository.findByPractitionerIdAndAppointmentDateBetween(
                nurseId, startOfDay, endOfDay);
        
        // If no appointments for the specified date, look for any appointments regardless of date
        if (appointments.isEmpty()) {
            System.out.println("No appointments found for the specified date. Looking for any appointments for this nurse...");
            appointments = appointmentRepository.findByPractitionerId(nurseId);
            
            // Modify appointment dates to be on the requested date (for testing only)
            if (!appointments.isEmpty()) {
                System.out.println("Found " + appointments.size() + " appointments for nurse (from any date). Setting them to " + date);
                int hour = 9; // Start at 9 AM
                
                for (int i = 0; i < appointments.size(); i++) {
                    Appointment appt = appointments.get(i);
                    LocalDateTime newDate = date.atTime(hour, 0);
                    appt.setAppointmentDate(newDate);
                    appointmentRepository.save(appt);
                    
                    // Increment hour for the next appointment, ensuring a good spread throughout the day
                    hour = (hour + 1) % 8 + 9; // 9 AM to 4 PM (9, 10, 11, 12, 1, 2, 3, 4)
                }
            }
        }
        
        System.out.println("Found " + appointments.size() + " appointments for nurse on " + date);
        
        if (appointments.isEmpty()) {
            // No appointments for this date
            System.out.println("No appointments found, creating empty schedule");
            return createEmptySchedule(nurseId, date);
        }
        
        // Display appointment details for debugging
        for (Appointment appt : appointments) {
            System.out.println("Appointment: ID=" + appt.getAppointmentId() + 
                               ", Patient=" + appt.getPatientId() + 
                               ", Date=" + appt.getAppointmentDate());
        }
        
        // Find patients for these appointments
        List<String> patientIds = appointments.stream()
                .map(Appointment::getPatientId)
                .collect(Collectors.toList());
        
        System.out.println("Patient IDs from appointments: " + patientIds);
        
        List<Patient> patients = new ArrayList<>();
        for (String patientId : patientIds) {
            Long numericId = Long.parseLong(Math.abs(patientId.hashCode()) + "");
            System.out.println("Looking for patient with numeric ID " + numericId + 
                               " (from original ID " + patientId + ")");
            
            patientRepository.findById(numericId).ifPresent(patient -> {
                System.out.println("Found patient: " + patient.getName());
                patients.add(patient);
            });
        }
        
        System.out.println("Found " + patients.size() + " patients out of " + patientIds.size() + " appointments");
        
        if (patients.isEmpty()) {
            System.out.println("No patients found for appointments, creating empty schedule");
            return createEmptySchedule(nurseId, date);
        }
        
        // Build coordinates list for route calculation
        List<double[]> points = new ArrayList<>();
        
        // Start at nurse's home
        points.add(new double[]{nurse.getLatitude(), nurse.getLongitude()});
        System.out.println("Added nurse location: " + nurse.getLatitude() + ", " + nurse.getLongitude());
        
        // Add patient locations
        for (Patient patient : patients) {
            if (patient.getLatitude() != 0 && patient.getLongitude() != 0) {
                points.add(new double[]{patient.getLatitude(), patient.getLongitude()});
                System.out.println("Added patient location: " + patient.getLatitude() + ", " + patient.getLongitude());
            } else {
                System.out.println("Warning: Patient " + patient.getName() + " has no coordinates");
            }
        }
        
        // End at nurse's home (return to start)
        points.add(new double[]{nurse.getLatitude(), nurse.getLongitude()});
        
        // Calculate optimal route
        System.out.println("Calculating route with " + points.size() + " points");
        GraphHopperService.RouteResponse routeResponse;
        try {
            routeResponse = graphHopperService.calculateRoute(points);
            System.out.println("Route calculation successful. Distance: " + routeResponse.getDistance() + "m");
        } catch (Exception e) {
            System.err.println("Error calculating route: " + e.getMessage());
            e.printStackTrace();
            
            // Create a simple direct route using patient coordinates (bypassing GraphHopper)
            routeResponse = createSimpleRoute(points);
            System.out.println("Created simple route without GraphHopper. Distance: " + routeResponse.getDistance() + "m");
        }
        
        // Create a new schedule entity
        NurseSchedule schedule = new NurseSchedule();
        schedule.setNurseId(nurseId);
        schedule.setScheduleDate(date);
        schedule.setTotalDistance(routeResponse.getDistance());
        
        // Estimate travel time (assuming 50 km/h average speed)
        double distanceKm = routeResponse.getDistance() / 1000.0;
        int travelTimeMinutes = (int) (distanceKm / 50.0 * 60.0);
        schedule.setTotalTravelTime(travelTimeMinutes);
        
        // Set patient visit order
        schedule.setPatientVisitOrder(patientIds);
        
        // Convert route coordinates to JSON string
        try {
            String routeCoordinatesJson = objectMapper.writeValueAsString(routeResponse.getCoordinates());
            schedule.setRouteCoordinates(routeCoordinatesJson);
        } catch (JsonProcessingException e) {
            System.err.println("Error serializing route coordinates: " + e.getMessage());
            throw new RuntimeException("Error serializing route coordinates", e);
        }
        
        schedule.setStatus("GENERATED");
        schedule.setGeneratedDate(LocalDate.now());
        
        // Save and return the schedule
        NurseSchedule savedSchedule = nurseScheduleRepository.save(schedule);
        System.out.println("Saved new schedule with ID " + savedSchedule.getId());
        return savedSchedule;
    }
    
    /**
     * Create a simple direct route when GraphHopper fails
     */
    private GraphHopperService.RouteResponse createSimpleRoute(List<double[]> points) {
        List<double[]> routePoints = new ArrayList<>();
        double totalDistance = 0.0;
        
        // Add all points directly (straight lines between points)
        for (int i = 0; i < points.size() - 1; i++) {
            double[] start = points.get(i);
            double[] end = points.get(i + 1);
            
            // Add start point
            routePoints.add(start);
            
            // Calculate Euclidean distance (approximate in meters)
            double latDiff = end[0] - start[0];
            double lonDiff = end[1] - start[1];
            double distanceMeters = Math.sqrt(latDiff * latDiff + lonDiff * lonDiff) * 111320;
            totalDistance += distanceMeters;
        }
        
        // Add final point
        routePoints.add(points.get(points.size() - 1));
        
        return new GraphHopperService.RouteResponse(routePoints, totalDistance);
    }
    
    /**
     * Create an empty schedule when no appointments are found
     */
    private NurseSchedule createEmptySchedule(String nurseId, LocalDate date) {
        NurseSchedule schedule = new NurseSchedule();
        schedule.setNurseId(nurseId);
        schedule.setScheduleDate(date);
        schedule.setTotalDistance(0);
        schedule.setTotalTravelTime(0);
        schedule.setPatientVisitOrder(new ArrayList<>());
        schedule.setRouteCoordinates("[]");
        schedule.setStatus("EMPTY");
        schedule.setGeneratedDate(LocalDate.now());
        
        NurseSchedule savedSchedule = nurseScheduleRepository.save(schedule);
        System.out.println("Saved empty schedule with ID " + savedSchedule.getId());
        return savedSchedule;
    }
    
    /**
     * Find schedules for a nurse within a date range
     */
    public List<NurseSchedule> getSchedulesForNurse(String nurseId, LocalDate startDate, LocalDate endDate) {
        return nurseScheduleRepository.findByNurseIdAndScheduleDateBetween(nurseId, startDate, endDate);
    }
    
    /**
     * Find all schedules for a specific date
     */
    public List<NurseSchedule> getSchedulesForDate(LocalDate date) {
        return nurseScheduleRepository.findByScheduleDate(date);
    }
    
    /**
     * Update the status of a schedule
     */
    @Transactional
    public NurseSchedule updateScheduleStatus(Long scheduleId, String newStatus) {
        NurseSchedule schedule = nurseScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("Schedule not found with ID: " + scheduleId));
        
        schedule.setStatus(newStatus);
        return nurseScheduleRepository.save(schedule);
    }
    
    /**
     * Generate schedules for all nurses on a specific date
     */
    @Transactional
    public void generateSchedulesForAllNurses(LocalDate date) {
        System.out.println("Generating schedules for all nurses on " + date);
        List<Nurse> nurses = nurseRepository.findAll();
        
        for (Nurse nurse : nurses) {
            try {
                String nurseId = String.valueOf(nurse.getId());
                NurseSchedule schedule = generateSchedule(nurseId, date);
                System.out.println("Generated schedule for nurse " + nurse.getName() + " with " + 
                                  schedule.getPatientVisitOrder().size() + " patients");
            } catch (Exception e) {
                System.err.println("Error generating schedule for nurse " + nurse.getName() + ": " + e.getMessage());
            }
        }
    }
}