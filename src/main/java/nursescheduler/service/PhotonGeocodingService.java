package nursescheduler.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for geocoding addresses to coordinates using a local Photon instance
 * Maintains HIPAA compliance by not sending any data externally
 */
@Service
public class PhotonGeocodingService {

    @Value("${geocoding.photon.url:http://localhost:2322}")
    private String photonUrl;

    @Value("${geocoding.photon.limit:5}")
    private int limit;

    private final RestTemplate restTemplate = new RestTemplate();
    
    // Cache for previously geocoded addresses to improve performance
    private final Map<String, GeocodingResult> geocodeCache = new HashMap<>();

    /**
     * Convert an address string to latitude/longitude coordinates
     * 
     * @param address The address to geocode (e.g., "123 Main St, Austin, TX")
     * @return GeocodingResult containing the coordinates or null if not found
     */
    public GeocodingResult geocodeAddress(String address) {
        // Check cache first
        if (geocodeCache.containsKey(address)) {
            return geocodeCache.get(address);
        }
        
        try {
            String url = String.format("%s/api?q=%s&limit=%d", 
                    photonUrl, 
                    address.replace(" ", "+"), 
                    limit);
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> responseBody = response.getBody();
            
            if (responseBody != null && responseBody.containsKey("features")) {
                List<Map<String, Object>> features = (List<Map<String, Object>>) responseBody.get("features");
                
                if (!features.isEmpty()) {
                    Map<String, Object> feature = features.get(0);
                    Map<String, Object> geometry = (Map<String, Object>) feature.get("geometry");
                    List<Double> coordinates = (List<Double>) geometry.get("coordinates");
                    Map<String, Object> properties = (Map<String, Object>) feature.get("properties");
                    
                    // Photon returns coordinates as [lon, lat]
                    double longitude = coordinates.get(0);
                    double latitude = coordinates.get(1);
                    
                    // Construct formatted address from properties
                    String formattedAddress = constructFormattedAddress(properties);
                    
                    GeocodingResult result = new GeocodingResult(latitude, longitude, formattedAddress);
                    
                    // Cache the result
                    geocodeCache.put(address, result);
                    
                    return result;
                }
            }
            
            return null;
        } catch (Exception e) {
            System.err.println("Geocoding error: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Convert coordinates back to an address
     * 
     * @param latitude The latitude
     * @param longitude The longitude
     * @return String representation of the address or null if not found
     */
    public GeocodingResult reverseGeocode(double latitude, double longitude) {
        try {
            String url = String.format("%s/reverse?lat=%f&lon=%f", 
                    photonUrl, 
                    latitude, 
                    longitude);
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> responseBody = response.getBody();
            
            if (responseBody != null && responseBody.containsKey("features")) {
                List<Map<String, Object>> features = (List<Map<String, Object>>) responseBody.get("features");
                
                if (!features.isEmpty()) {
                    Map<String, Object> feature = features.get(0);
                    Map<String, Object> properties = (Map<String, Object>) feature.get("properties");
                    
                    // Construct formatted address from properties
                    String formattedAddress = constructFormattedAddress(properties);
                    
                    return new GeocodingResult(latitude, longitude, formattedAddress);
                }
            }
            
            return null;
        } catch (Exception e) {
            System.err.println("Reverse geocoding error: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Construct a formatted address from Photon properties
     */
    private String constructFormattedAddress(Map<String, Object> properties) {
        List<String> addressParts = new ArrayList<>();
        
        // Add components in order of specificity
        if (properties.containsKey("name")) {
            addressParts.add((String) properties.get("name"));
        }
        
        if (properties.containsKey("housenumber")) {
            addressParts.add((String) properties.get("housenumber"));
        }
        
        if (properties.containsKey("street")) {
            addressParts.add((String) properties.get("street"));
        }
        
        if (properties.containsKey("city")) {
            addressParts.add((String) properties.get("city"));
        }
        
        if (properties.containsKey("state")) {
            addressParts.add((String) properties.get("state"));
        }
        
        if (properties.containsKey("postcode")) {
            addressParts.add((String) properties.get("postcode"));
        }
        
        return String.join(", ", addressParts);
    }
    
    /**
     * Class to hold geocoding results
     */
    public static class GeocodingResult {
        private double latitude;
        private double longitude;
        private String formattedAddress;
        
        public GeocodingResult(double latitude, double longitude, String formattedAddress) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.formattedAddress = formattedAddress;
        }
        
        public double getLatitude() {
            return latitude;
        }
        
        public double getLongitude() {
            return longitude;
        }
        
        public String getFormattedAddress() {
            return formattedAddress;
        }
    }
}