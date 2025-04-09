package nursescheduler.controller;

import nursescheduler.model.Nurse;
import nursescheduler.model.Patient;
import nursescheduler.repository.NurseRepository;
import nursescheduler.repository.PatientRepository;
import nursescheduler.service.GraphHopperService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private GraphHopperService graphHopperService;

    @Autowired
    private NurseRepository nurseRepository;

    @Autowired
    private PatientRepository patientRepository;

    @GetMapping("/nurse")
    public Map<String, Object> getNurse() {
        Map<String, Object> response = new HashMap<>();
        try {
            // For simplicity, return the first nurse (you can modify this logic as needed)
            Nurse nurse = nurseRepository.findAll().stream().findFirst().orElse(null);
            response.put("success", true);
            response.put("nurse", nurse);
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
            List<Patient> patients = patientRepository.findAll();
            response.put("success", true);
            response.put("patients", patients);
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