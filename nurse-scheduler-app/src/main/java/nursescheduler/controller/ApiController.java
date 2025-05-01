package nursescheduler.controller;

import nursescheduler.model.Patient;
// Probably don't need but just incase 
// import nursescheduler.model.Nurse;
// import nursescheduler.model.Patient;
// import nursescheduler.repository.NurseRepository;
// import nursescheduler.repository.PatientRepository;
import nursescheduler.service.GraphHopperService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private GraphHopperService graphHopperService;

    @Autowired
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    public ApiController(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/nurses")
    public Map<String, Object> getNurses() {
        Map<String, Object> response = new HashMap<>();
        try {
            Resource resource = resourceLoader.getResource("classpath:static/JSON/workers.json");
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> workers = mapper.readValue(resource.getInputStream(), List.class);
            response.put("success", true);
            response.put("nurses", workers);
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }

    @GetMapping("/patients")
    public Map<String, Object> getPatients(@RequestParam(required = false) String workerId) {
        Map<String, Object> response = new HashMap<>();
        try {
            Resource patientResource = resourceLoader.getResource("classpath:static/json/patients.json");
            Resource appointmentResource = resourceLoader.getResource("classpath:/static/json/appointments.json");
            List<Map<String, Object>> patients = objectMapper.readValue(patientResource.getInputStream(), List.class);
            List<Map<String, Object>> appointments = objectMapper.readValue(appointmentResource.getInputStream(), List.class);

            List<Map<String, Object>> filteredPatients = patients;
            if (workerId != null) {
                List<String> patientIds = appointments.stream()
                .filter(a -> workerId.equals(String.valueOf(a.get("practitionerId"))))
                .map(a -> String.valueOf(a.get("patientId")))
                .collect(Collectors.toList());
                filteredPatients = patients.stream()
                .filter(p -> patientIds.contains(String.valueOf(p.get("patientId"))))
                .collect(Collectors.toList());
            }

            response.put("success", true);
            response.put("patients", filteredPatients);
            return response;
        } catch (IOException e) {
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

    // @GetMapping("/points")
    // public Map<String, Object> getPoints() {
    //     Map<String, Object> response = new HashMap<>();
    //     try {
    //         Resource resource = resourceLoader.getResource("classpath:static/json/points.json");
    //         ObjectMapper mapper = new ObjectMapper();
    //         Map<String, Object> pointsData = mapper.readValue(resource.getInputStream(), Map.class);
    //         response.put("success", true);
    //         response.put("nurse", pointsData.get("nurse"));
    //         response.put("patients", pointsData.get("patients"));
    //         return response;
    //     } catch (Exception e) {
    //         response.put("success", false);
    //         response.put("error", "Failed to read JSON file: " + e.getMessage());
    //         return response;
    //     }
    // }
}