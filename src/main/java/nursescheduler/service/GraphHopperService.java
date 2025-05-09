package nursescheduler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingTransportCosts;
import com.graphhopper.jsprit.core.problem.driver.Driver;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.vehicle.Vehicle;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleType;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.util.Coordinate;
import com.graphhopper.jsprit.core.util.Solutions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class GraphHopperService {

    @Value("${graphhopper.url:http://localhost:8989}")
    private String graphHopperUrl;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final int WEIGHT_INDEX = 0;

    public RouteResponse calculateRoute(List<double[]> points) {
        try {
            // The first point is the nurse's starting location
            double[] nurseLocation = points.get(0);

            // Define the vehicle type with a capacity of 10 (arbitrary, since we don't have capacity constraints)
            VehicleType vehicleType = VehicleTypeImpl.Builder.newInstance("vehicleType")
                .addCapacityDimension(WEIGHT_INDEX, 10)
                .build();

            // Define the vehicle starting at the nurse's location
            Location vehicleLocation = Location.Builder.newInstance()
                .setId("vehicle")
                .setCoordinate(Coordinate.newInstance(nurseLocation[0], nurseLocation[1]))
                .build();

            VehicleImpl vehicle = VehicleImpl.Builder.newInstance("vehicle")
                .setStartLocation(vehicleLocation)
                .setType(vehicleType)
                .setReturnToDepot(true)  // Ensure the vehicle returns to the start location
                .build();

            // Define services (patient locations) starting from the second point
            List<com.graphhopper.jsprit.core.problem.job.Service> services = new ArrayList<>();
            for (int i = 1; i < points.size(); i++) {
                double[] point = points.get(i);
                Location patientLocation = Location.Builder.newInstance()
                    .setId("service_" + i)
                    .setCoordinate(Coordinate.newInstance(point[0], point[1]))
                    .build();

                com.graphhopper.jsprit.core.problem.job.Service service = com.graphhopper.jsprit.core.problem.job.Service.Builder.newInstance("service_" + i)
                    .addSizeDimension(WEIGHT_INDEX, 1)  // Each patient has a demand of 1
                    .setLocation(patientLocation)
                    .build();
                services.add(service);
            }

            // Build a transport cost matrix using Euclidean distances
            VehicleRoutingTransportCosts transportCosts = new EuclideanTransportCosts(points);

            // Build the vehicle routing problem
            VehicleRoutingProblem problem = VehicleRoutingProblem.Builder.newInstance()
                .addVehicle(vehicle)
                .addAllJobs(services)
                .setRoutingCost(transportCosts)
                .build();

            // Solve the problem using jsprit's default algorithm
            VehicleRoutingAlgorithm algorithm = Jsprit.createAlgorithm(problem);
            Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();
            VehicleRoutingProblemSolution bestSolution = Solutions.bestOf(solutions);

            // Extract the ordered points from the solution
            List<double[]> orderedPoints = new ArrayList<>();
            for (VehicleRoute route : bestSolution.getRoutes()) {
                // Start location
                orderedPoints.add(new double[]{route.getStart().getLocation().getCoordinate().getX(),
                                               route.getStart().getLocation().getCoordinate().getY()});
                // Activities (patient visits)
                route.getActivities().forEach(activity -> {
                    orderedPoints.add(new double[]{activity.getLocation().getCoordinate().getX(),
                                                   activity.getLocation().getCoordinate().getY()});
                });
                // End location (return to start)
                orderedPoints.add(new double[]{route.getEnd().getLocation().getCoordinate().getX(),
                                               route.getEnd().getLocation().getCoordinate().getY()});
            }

            // Use standalone GraphHopper to calculate the actual road path for the ordered points
            List<double[]> coordinates = new ArrayList<>();
            double totalDistance = 0.0;
            
            for (int i = 0; i < orderedPoints.size() - 1; i++) {
                // Build GraphHopper REST API request URL
                String url = String.format("%s/route?point=%.6f,%.6f&point=%.6f,%.6f&vehicle=car&calc_points=true&points_encoded=false",
                    graphHopperUrl,
                    orderedPoints.get(i)[0], orderedPoints.get(i)[1],
                    orderedPoints.get(i + 1)[0], orderedPoints.get(i + 1)[1]);
                
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    // Parse GraphHopper response
                    try {
                        Map<String, Object> responseMap = objectMapper.readValue(response.getBody(), Map.class);
                        List<Map<String, Object>> paths = (List<Map<String, Object>>) responseMap.get("paths");
                        
                        if (paths != null && !paths.isEmpty()) {
                            Map<String, Object> path = paths.get(0);
                            totalDistance += ((Number) path.get("distance")).doubleValue();
                            
                            // Extract points from the response
List<List<Double>> pointsFromResponse = (List<List<Double>>) path.get("points");

if (pointsFromResponse != null) {
    for (List<Double> point : pointsFromResponse) {
        // GraphHopper returns [lon, lat], but we need [lat, lon]
        coordinates.add(new double[]{point.get(1), point.get(0)});
    }
}
                        }
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException("Error parsing GraphHopper response: " + e.getMessage(), e);
                    }
                } else {
                    throw new RuntimeException("Error from GraphHopper API: " + response.getStatusCode());
                }
            }

            return new RouteResponse(coordinates, totalDistance);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate route: " + e.getMessage(), e);
        }
    }

    // Custom transport costs using Euclidean distances
    private class EuclideanTransportCosts implements VehicleRoutingTransportCosts {
        private final List<double[]> points;

        public EuclideanTransportCosts(List<double[]> points) {
            this.points = points;
        }

        @Override
        public double getDistance(Location from, Location to, double departureTime, Vehicle vehicle) {
            int fromIndex = from.getId().equals("vehicle") ? 0 : Integer.parseInt(from.getId().replace("service_", ""));
            int toIndex = to.getId().equals("vehicle") ? 0 : Integer.parseInt(to.getId().replace("service_", ""));
            double lat1 = points.get(fromIndex)[0];
            double lon1 = points.get(fromIndex)[1];
            double lat2 = points.get(toIndex)[0];
            double lon2 = points.get(toIndex)[1];
            // Approximate Euclidean distance in meters (using a simple conversion factor)
            return Math.sqrt(Math.pow(lat2 - lat1, 2) + Math.pow(lon2 - lon1, 2)) * 111320;
        }

        @Override
        public double getTransportCost(Location from, Location to, double departureTime, Driver driver, Vehicle vehicle) {
            return getDistance(from, to, departureTime, vehicle);
        }

        @Override
        public double getTransportTime(Location from, Location to, double departureTime, Driver driver, Vehicle vehicle) {
            return getTransportCost(from, to, departureTime, driver, vehicle);  // Use distance as a proxy for time
        }

        @Override
        public double getBackwardTransportTime(Location from, Location to, double arrivalTime, Driver driver, Vehicle vehicle) {
            return getTransportTime(from, to, arrivalTime, driver, vehicle);
        }

        @Override
        public double getBackwardTransportCost(Location from, Location to, double arrivalTime, Driver driver, Vehicle vehicle) {
            return getTransportCost(from, to, arrivalTime, driver, vehicle);
        }
    }

    public static class RouteResponse {
        private final List<double[]> coordinates;
        private final double distance;

        public RouteResponse(List<double[]> coordinates, double distance) {
            this.coordinates = coordinates;
            this.distance = distance;
        }

        public List<double[]> getCoordinates() {
            return coordinates;
        }

        public double getDistance() {
            return distance;
        }
    }
}