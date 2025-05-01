package nursescheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RoutePrecalculationService {

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private GraphHopperService graphHopperService;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, GraphHopperService.RouteResponse> routeCache = new HashMap<>();

    @PostConstruct
    public void precalculateRoutes() {
        try {
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

            // Filter appointments for today
            String today = LocalDate.now().toString();
            Map<String, List<Map<String, Object>>> appointmentsByNurse = appointments.stream()
                .filter(a -> today.equals(String.valueOf(a.get("appointmentDate")).substring(0, 10)))
                .collect(Collectors.groupingBy(a -> String.valueOf(a.get("practitionerId"))));

            // Calculate routes for each nurse
            for (Map<String, Object> worker : workers) {
                String workerId = String.valueOf(worker.get("workerId"));
                List<Map<String, Object>> nurseAppointments = appointmentsByNurse.getOrDefault(workerId, List.of());

                // Get patient IDs for the nurse
                List<String> patientIds = nurseAppointments.stream()
                    .map(a -> String.valueOf(a.get("patientId")))
                    .collect(Collectors.toList());

                // Get patients for the nurse
                List<Map<String, Object>> assignedPatients = patients.stream()
                    .filter(p -> patientIds.contains(String.valueOf(p.get("patientId"))))
                    .collect(Collectors.toList());

                // Get nurse coordinates
                Map<String, Object> nurseCoords = (Map<String, Object>) worker.get("coordinates");
                double[] nurseCoordArray = new double[]{(double) nurseCoords.get("latitude"), (double) nurseCoords.get("longitude")};

                // Build coordinate list: [nurse, patients, nurse]
                List<double[]> points = new ArrayList<>();
                points.add(nurseCoordArray);

                for (Map<String, Object> patient : assignedPatients) {
                    Map<String, Object> patientCoords = (Map<String, Object>) patient.get("coordinates");
                    points.add(new double[]{(double) patientCoords.get("latitude"), (double) patientCoords.get("longitude")});
                }

                if (!assignedPatients.isEmpty()) {
                    points.add(nurseCoordArray);
                }

                // Calculate route if there are patients
                if (points.size() > 1) {
                    GraphHopperService.RouteResponse route = graphHopperService.calculateRoute(points);
                    routeCache.put(workerId, route);
                }
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
