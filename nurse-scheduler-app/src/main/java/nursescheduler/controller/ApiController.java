package nursescheduler.controller;

import nursescheduler.model.Patient;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private GraphHopperService graphHopperService;

    // @Autowired
    // private NurseRepository nurseRepository;

    // @Autowired
    // private PatientRepository patientRepository;

    @Autowired
    private ResourceLoader resourceLoader;

    @GetMapping("/nurse")
    public Map<String, Object> getNurse() {
        Map<String, Object> response = new HashMap<>();
        try {
           Resource resource = resourceLoader.getResource("classpath:static/json/workers.json");
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> workersData = mapper.readValue(resource.getInputStream(), Map.class);
            List<Map<String, Object>> workers = (List<Map<String, Object>>) workersData.get("workers");
            
            // Take the first worker as the nurse
            Map<String, Object> nurse = workers.isEmpty() ? new HashMap<>() : workers.get(0);
            Map<String, Object> nurseResponse = new HashMap<>();
            nurseResponse.put("name", nurse.get("firstName") + " " + nurse.get("lastName"));
            Map<String, Object> coordinates = (Map<String, Object>) nurse.get("coordinates");
            nurseResponse.put("latitude", coordinates.get("latitude"));
            nurseResponse.put("longitude", coordinates.get("longitude"));
            
            response.put("success", true);
            response.put("nurse", nurseResponse);
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }

    @GetMapping("/patients")
    public Map<String, Object> getPatients() {
        Map<String, Object> response = new HashMap<>();
        try {
            Resource resource = resourceLoader.getResource("classpath:static/json/patients.json");
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> patientsData = mapper.readValue(resource.getInputStream(), Map.class);
            List<Map<String, Object>> patients = (List<Map<String, Object>>) patientsData.get("patients");
            
            // Map patients to include only name, latitude, and longitude
            List<Map<String, Object>> patientsResponse = patients.stream().map(patient -> {
                Map<String, Object> patientResponse = new HashMap<>();
                patientResponse.put("name", patient.get("firstName") + " " + patient.get("lastName"));
                Map<String, Object> coordinates = (Map<String, Object>) patient.get("coordinates");
                patientResponse.put("latitude", coordinates.get("latitude"));
                patientResponse.put("longitude", coordinates.get("longitude"));
                return patientResponse;
            }).toList();
            
            response.put("success", true);
            response.put("patients", patientsResponse);
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