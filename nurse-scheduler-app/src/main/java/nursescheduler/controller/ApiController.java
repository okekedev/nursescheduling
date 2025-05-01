package nursescheduler.controller;

import nursescheduler.service.GraphHopperService;
import nursescheduler.service.RoutePrecalculationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private GraphHopperService graphHopperService;

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private RoutePrecalculationService routePrecalculationService;

    @Autowired
    private ObjectMapper mapper;

    @GetMapping("/schedule")
    public Map<String, Object> getSchedule(@RequestParam(required = false) String workerId, @RequestParam(required = false) String date) {
        Map<String, Object> response = new HashMap<>();
        try {
            // Default to today's date if not provided
            String filterDate = date != null ? date : LocalDate.now().toString();

            // Read JSON files
            Resource workerResource = resourceLoader.getResource("classpath:static/JSON/workers.json");
            Resource patientResource = resourceLoader.getResource("classpath:static/JSON/patients.json");
            Resource appointmentResource = resourceLoader.getResource("classpath:static/JSON/appointments.json");

            Map<String, Object> workersData = mapper.readValue(workerResource.getInputStream(), Map.class);
            Map<String, Object> patientsData = mapper.readValue(patientResource.getInputStream(), Map.class);
            Map<String, Object> appointmentsData = mapper.readValue(appointmentResource.getInputStream(), Map.class);

            List<Map<String, Object>> workers = (List<Map<String, Object>>) workersData.get("workers");
            List<Map<String, Object>> patients = (List<Map<String, Object>>) patientsData.get("patients");
            List<Map<String, Object>> appointments = (List<Map<String, Object>>) ((Map<String, Object>) appointmentsData.get("appointments")).get("all");

            // Filter workers if workerId is provided
            List<Map<String, Object>> filteredWorkers = workerId != null
                ? workers.stream()
                    .filter(w -> workerId.equals(String.valueOf(w.get("workerId"))))
                    .collect(Collectors.toList())
                : workers;

            // Build schedules
            List<Map<String, Object>> schedules = new ArrayList<>();
            for (Map<String, Object> worker : filteredWorkers) {
                String currentWorkerId = String.valueOf(worker.get("workerId"));

                // Filter appointments by workerId and date (extract YYYY-MM-DD)
                List<String> patientIds = appointments.stream()
                    .filter(a -> currentWorkerId.equals(String.valueOf(a.get("practitionerId")))
                        && filterDate.equals(String.valueOf(a.get("appointmentDate")).substring(0, 10)))
                    .map(a -> String.valueOf(a.get("patientId")))
                    .collect(Collectors.toList());

                // Get assigned patients
                List<Map<String, Object>> assignedPatients = patients.stream()
                    .filter(p -> patientIds.contains(String.valueOf(p.get("patientId"))))
                    .map(patient -> {
                        Map<String, Object> patientResponse = new HashMap<>();
                        patientResponse.put("name", patient.get("firstName") + " " + patient.get("lastName"));
                        patientResponse.put("patientId", patient.get("patientId"));
                        Map<String, Object> coordinates = (Map<String, Object>) patient.get("coordinates");
                        patientResponse.put("latitude", coordinates.get("latitude"));
                        patientResponse.put("longitude", coordinates.get("longitude"));
                        return patientResponse;
                    })
                    .collect(Collectors.toList());

                // Get pre-calculated route
                GraphHopperService.RouteResponse route = routePrecalculationService.getRoute(currentWorkerId);
                Map<String, Object> routeData = route != null
                    ? Map.of("coordinates", route.getCoordinates(), "distance", route.getDistance())
                    : Map.of("coordinates", List.of(), "distance", 0.0);

                // Build schedule entry
                Map<String, Object> schedule = new HashMap<>();
                schedule.put("nurse", worker);
                schedule.put("patients", assignedPatients);
                schedule.put("route", routeData);
                schedules.add(schedule);
            }

            response.put("success", true);
            response.put("schedules", schedules);
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }

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
}