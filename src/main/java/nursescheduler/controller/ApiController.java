package nursescheduler.controller;

import nursescheduler.model.Appointment;
import nursescheduler.model.Nurse;
import nursescheduler.model.Patient;
import nursescheduler.model.NurseSchedule;
import nursescheduler.repository.AppointmentRepository;
import nursescheduler.repository.NurseRepository;
import nursescheduler.repository.PatientRepository;
import nursescheduler.repository.NurseScheduleRepository;
import nursescheduler.service.GraphHopperService;
import nursescheduler.service.NurseScheduleService;
import nursescheduler.service.RoutePrecalculationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ApiController {

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

    @Autowired
    private RoutePrecalculationService routePrecalculationService;

    @Autowired
    private NurseScheduleService nurseScheduleService;

    /**
     * Get nurse information
     */
    @GetMapping("/nurse")
    public Map<String, Object> getNurse(@RequestParam(required = false) String id) {
        Map<String, Object> response = new HashMap<>();
        try {
            Nurse nurse;
            
            if (id != null) {
                nurse = nurseRepository.findById(Long.parseLong(id)).orElse(null);
            } else {
                nurse = nurseRepository.findAll().stream().findFirst().orElse(null);
            }
            
            if (nurse != null) {
                response.put("success", true);
                response.put("nurse", nurse);
            } else {
                response.put("success", false);
                response.put("error", "No nurse found");
            }
            
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }

    /**
     * Get all nurses
     */
    @GetMapping("/nurses")
    public Map<String, Object> getAllNurses() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Nurse> nurses = nurseRepository.findAll();
            
            response.put("success", true);
            response.put("nurses", nurses);
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }

    /**
     * Get patients with optional filtering by nurse and limiting results
     */
    @GetMapping("/patients")
    public Map<String, Object> getPatients(
            @RequestParam(required = false) String nurseId,
            @RequestParam(required = false, defaultValue = "30") int limit) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Patient> patients;
            
            if (nurseId != null) {
                // Find appointments for this nurse
                List<Appointment> appointments = appointmentRepository.findByPractitionerId(nurseId);
                
                if (appointments.size() > limit) {
                    System.out.println("Warning: Nurse " + nurseId + " has " + appointments.size() + 
                                      " appointments. Limiting to " + limit);
                    appointments = appointments.subList(0, limit);
                }
                
                // Get patient IDs from appointments
                List<String> patientIds = appointments.stream()
                    .map(Appointment::getPatientId)
                    .collect(Collectors.toList());
                
                // Find patients by IDs (converted to numeric IDs)
                patients = new ArrayList<>();
                for (String patientId : patientIds) {
                    Long numericId = Long.parseLong(Math.abs(patientId.hashCode()) + "");
                    patientRepository.findById(numericId).ifPresent(patients::add);
                }
            } else {
                // If no nurse ID provided, get all patients with limit
                patients = patientRepository.findAll();
                if (patients.size() > limit) {
                    patients = patients.subList(0, limit);
                }
            }
            
            response.put("success", true);
            response.put("patients", patients);
            response.put("total", patients.size());
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }

    /**
     * Get nurse schedule with patients and route
     */
    @GetMapping("/schedule")
    public Map<String, Object> getSchedule(
            @RequestParam(required = false) String nurseId,
            @RequestParam(required = false) String date) {
        Map<String, Object> response = new HashMap<>();
        try {
            // Get the nurse (either by ID or the first one if no ID provided)
            Nurse nurse;
            if (nurseId != null) {
                nurse = nurseRepository.findById(Long.parseLong(nurseId)).orElse(null);
            } else {
                nurse = nurseRepository.findAll().stream().findFirst().orElse(null);
            }
            
            if (nurse == null) {
                response.put("success", false);
                response.put("error", "Nurse not found");
                return response;
            }
            
            // Parse date or use today
            LocalDate scheduleDate;
            if (date != null) {
                scheduleDate = LocalDate.parse(date);
            } else {
                scheduleDate = LocalDate.now();
            }
            
            // Get or generate the schedule for this nurse and date
            NurseSchedule schedule = nurseScheduleService.getOrGenerateSchedule(
                    String.valueOf(nurse.getId()), scheduleDate);
            
            // Get patients for this schedule
            List<Patient> patients = new ArrayList<>();
            if (schedule != null && schedule.getPatientVisitOrder() != null) {
                for (String patientId : schedule.getPatientVisitOrder()) {
                    Long numericId = Long.parseLong(Math.abs(patientId.hashCode()) + "");
                    patientRepository.findById(numericId).ifPresent(patients::add);
                }
            }
            
            // Build response
            Map<String, Object> scheduleData = new HashMap<>();
            scheduleData.put("nurse", nurse);
            scheduleData.put("patients", patients);
            scheduleData.put("date", scheduleDate.toString());
            scheduleData.put("totalDistance", schedule.getTotalDistance());
            scheduleData.put("travelTime", schedule.getTotalTravelTime());
            scheduleData.put("status", schedule.getStatus());
            
            // Parse route coordinates from JSON if present
            if (schedule.getRouteCoordinates() != null && !schedule.getRouteCoordinates().equals("[]")) {
                // In a real implementation, you would parse the JSON string to a List<double[]>
                // For now, we'll just include the raw JSON string
                scheduleData.put("routeCoordinates", schedule.getRouteCoordinates());
            } else {
                scheduleData.put("routeCoordinates", "[]");
            }
            
            response.put("success", true);
            response.put("schedule", scheduleData);
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }

    /**
     * Generate a schedule for a nurse
     */
    @PostMapping("/schedule/generate")
    public Map<String, Object> generateSchedule(
            @RequestParam String nurseId,
            @RequestParam(required = false) String date) {
        Map<String, Object> response = new HashMap<>();
        try {
            // Parse date or use today
            LocalDate scheduleDate;
            if (date != null) {
                scheduleDate = LocalDate.parse(date);
            } else {
                scheduleDate = LocalDate.now();
            }
            
            // Force generate a new schedule
            NurseSchedule schedule = nurseScheduleService.generateSchedule(nurseId, scheduleDate);
            
            response.put("success", true);
            response.put("message", "Schedule generated successfully");
            response.put("scheduleId", schedule.getId());
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }

    /**
     * Calculate a route for the given points
     */
    @PostMapping("/route")
    public Map<String, Object> calculateRoute(@RequestBody List<double[]> points) {
        try {
            GraphHopperService.RouteResponse response = graphHopperService.calculateRoute(points);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("coordinates", response.getCoordinates());
            result.put("distance", response.getDistance());
            
            return result;
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return error;
        }
    }

    /**
     * Generate schedules for all nurses for today
     */
    @GetMapping("/generateAllSchedules")
    public Map<String, Object> generateAllSchedules() {
        Map<String, Object> response = new HashMap<>();
        try {
            LocalDate today = LocalDate.now();
            List<Nurse> nurses = nurseRepository.findAll();
            int count = 0;
            
            for (Nurse nurse : nurses) {
                try {
                    nurseScheduleService.generateSchedule(String.valueOf(nurse.getId()), today);
                    count++;
                } catch (Exception e) {
                    System.err.println("Error generating schedule for nurse " + nurse.getId() + ": " + e.getMessage());
                }
            }
            
            response.put("success", true);
            response.put("message", "Generated " + count + " schedules for " + nurses.size() + " nurses");
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }
}