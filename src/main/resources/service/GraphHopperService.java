package nursescheduler.service;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
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
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.PointList;
import com.graphhopper.json.Statement;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Service
public class GraphHopperService {

    private GraphHopper graphHopper;
    private static final int WEIGHT_INDEX = 0;

    @PostConstruct
    public void init() {
        graphHopper = new GraphHopper();
        graphHopper.setOSMFile("graphhopper/texas-latest.osm.pbf");
        graphHopper.setGraphHopperLocation("src/main/resources/graphhopper/graph-cache");

        // Set the encoded values used in the CustomModel
        graphHopper.setEncodedValuesString("road_access");

        // Define a custom model to handle road_access == DESTINATION and distance_influence
        CustomModel customModel = new CustomModel();
        customModel.addToSpeed(Statement.If("true", Statement.Op.LIMIT, "50"));  // Default speed: 50 km/h
        customModel.addToPriority(Statement.If("road_access == DESTINATION", Statement.Op.MULTIPLY, "0"));
        customModel.setDistanceInfluence(1000.0);

        // Define a profile with custom weighting
        Profile carProfile = new Profile("car")
            .setWeighting("custom")
            .setCustomModel(customModel);

        graphHopper.setProfiles(Collections.singletonList(carProfile));
        graphHopper.importOrLoad();
    }

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

            // Use GraphHopper to calculate the actual road path for the ordered points
            List<double[]> coordinates = new ArrayList<>();
            double totalDistance = 0.0;
            for (int i = 0; i < orderedPoints.size() - 1; i++) {
                GHRequest request = new GHRequest();
                request.addPoint(new com.graphhopper.util.shapes.GHPoint(orderedPoints.get(i)[0], orderedPoints.get(i)[1]));
                request.addPoint(new com.graphhopper.util.shapes.GHPoint(orderedPoints.get(i + 1)[0], orderedPoints.get(i + 1)[1]));
                request.setProfile("car");

                GHResponse response = graphHopper.route(request);
                if (response.hasErrors()) {
                    throw new RuntimeException("Error calculating route segment: " + response.getErrors());
                }

                PointList routePoints = response.getBest().getPoints();
                for (int j = 0; j < routePoints.size(); j++) {
                    coordinates.add(new double[]{routePoints.getLat(j), routePoints.getLon(j)});
                }
                totalDistance += response.getBest().getDistance();
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