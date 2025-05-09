package nursescheduler.service;

import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import nursescheduler.model.Nurse;
import nursescheduler.model.Patient;
import nursescheduler.repository.NurseRepository;
import nursescheduler.repository.PatientRepository;

@Service
public class RoutePrecalculationService {

    @Autowired
    private NurseRepository nurseRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private GraphHopperService graphHopperService;

    private final Map<String, GraphHopperService.RouteResponse> routeCache = new HashMap<>();

    @PostConstruct
    public void precalculateRoutes() {
        try {
            // Get all nurses from the database
            List<Nurse> nurses = nurseRepository.findAll();
            
            // List to store nurses with missing coordinates
            List<String> nursesMissingCoords = new ArrayList<>();

            // For each nurse, calculate a route for their patients
            for (Nurse nurse : nurses) {
                String nurseId = String.valueOf(nurse.getId());
                String nurseName = nurse.getName();
                
                // Check if nurse has coordinates
                if (nurse.getLatitude() == 0 || nurse.getLongitude() == 0) {
                    nursesMissingCoords.add("Nurse ID: " + nurseId + ", Name: " + nurseName);
                    continue; // Skip this nurse if coordinates are missing
                }
                
                // Get all patients (in a real app, you would filter by assigned patients)
                List<Patient> patients = patientRepository.findAll();
                
                // Build coordinate list: [nurse, patients, nurse]
                List<double[]> points = new ArrayList<>();
                points.add(new double[]{nurse.getLatitude(), nurse.getLongitude()});
                
                for (Patient patient : patients) {
                    if (patient.getLatitude() != 0 && patient.getLongitude() != 0) {
                        points.add(new double[]{patient.getLatitude(), patient.getLongitude()});
                    }
                }
                
                // Add nurse's location again as the end point
                if (!patients.isEmpty()) {
                    points.add(new double[]{nurse.getLatitude(), nurse.getLongitude()});
                }
                
                // Calculate route if there are patients
                if (points.size() > 1) {
                    GraphHopperService.RouteResponse route = graphHopperService.calculateRoute(points);
                    routeCache.put(nurseId, route);
                }
            }
            
            // Log nurses with missing coordinates
            if (!nursesMissingCoords.isEmpty()) {
                System.out.println("Nurses with missing coordinates:");
                for (String nurseInfo : nursesMissingCoords) {
                    System.out.println(nurseInfo);
                }
            } else {
                System.out.println("All nurses have valid coordinates.");
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to precalculate routes: " + e.getMessage(), e);
        }
    }

    public GraphHopperService.RouteResponse getRoute(String workerId) {
        return routeCache.get(workerId);
    }

    public Map<String, GraphHopperService.RouteResponse> getAllRoutes() {
        return new HashMap<>(routeCache);
    }
}