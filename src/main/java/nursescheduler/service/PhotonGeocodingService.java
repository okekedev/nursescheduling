package nursescheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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
    private final ObjectMapper mapper = new ObjectMapper();
    
    // Cache for previously geocoded addresses to improve performance
    private final Map<String, GeocodingResult> geocodeCache = new HashMap<>();

    // Log file
    private PrintWriter logWriter;

    /**
     * Initialize the service and create log file
     */
    public PhotonGeocodingService() {
        try {
            logWriter = new PrintWriter(new FileWriter("photon-geocoding.log", true));
            log("--- PhotonGeocodingService started at " + LocalDateTime.now() + " ---");
        } catch (IOException e) {
            System.err.println("Error creating log file: " + e.getMessage());
            // Create a dummy writer to avoid null checks
            logWriter = new PrintWriter(System.err);
        }
    }
    
    /**
     * Convert an address string to latitude/longitude coordinates
     * 
     * @param address The address to geocode (e.g., "123 Main St, Austin, TX")
     * @return GeocodingResult containing the coordinates or null if not found
     */
    public GeocodingResult geocodeAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            log("WARNING: Empty address provided for geocoding");
            return null;
        }
        
        // Clean the address string
        address = address.trim();
        log("\n==== Geocoding address: " + address + " ====");
        
        // Check cache first
        if (geocodeCache.containsKey(address)) {
            GeocodingResult cachedResult = geocodeCache.get(address);
            log("CACHE HIT: Using cached coordinates: " + cachedResult.getLatitude() + ", " + cachedResult.getLongitude());
            return cachedResult;
        }
        
        log("CACHE MISS: Address not found in cache, calling Photon API");
        
        try {
            // Encode the address for URL
            String encodedAddress = URLEncoder.encode(address, StandardCharsets.UTF_8.toString());
            String url = String.format("%s/api?q=%s&limit=%d", photonUrl, encodedAddress, limit);
            
            log("API REQUEST: " + url);
            
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            log("API STATUS: " + response.getStatusCode());
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Log raw response for debugging
                String responseBody = response.getBody();
                
                // Log only the first 1000 characters if too long
                if (responseBody.length() > 1000) {
                    log("API RESPONSE (truncated): " + responseBody.substring(0, 1000) + "...");
                } else {
                    log("API RESPONSE: " + responseBody);
                }
                
                // Parse the JSON response
                Map<String, Object> jsonMap = mapper.readValue(responseBody, Map.class);
                
                if (jsonMap.containsKey("features")) {
                    List<Map<String, Object>> features = (List<Map<String, Object>>) jsonMap.get("features");
                    
                    if (!features.isEmpty()) {
                        log("Found " + features.size() + " results");
                        
                        // Extract and log all results for comparison
                        for (int i = 0; i < features.size(); i++) {
                            Map<String, Object> feature = features.get(i);
                            Map<String, Object> geometry = (Map<String, Object>) feature.get("geometry");
                            List<Double> coordinates = (List<Double>) geometry.get("coordinates");
                            Map<String, Object> properties = (Map<String, Object>) feature.get("properties");
                            
                            // Photon returns coordinates as [lon, lat]
                            double longitude = coordinates.get(0);
                            double latitude = coordinates.get(1);
                            
                            String formattedAddress = constructFormattedAddress(properties);
                            log("Result #" + (i+1) + ": " + latitude + ", " + longitude + " - " + formattedAddress);
                            
                            // Log property details for the first result
                            if (i == 0) {
                                log("Properties for first result:");
                                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                                    log("  " + entry.getKey() + ": " + entry.getValue());
                                }
                            }
                        }
                        
                        // Use the first (best) result
                        Map<String, Object> feature = features.get(0);
                        Map<String, Object> geometry = (Map<String, Object>) feature.get("geometry");
                        List<Double> coordinates = (List<Double>) geometry.get("coordinates");
                        Map<String, Object> properties = (Map<String, Object>) feature.get("properties");
                        
                        // Photon returns coordinates as [lon, lat]
                        double longitude = coordinates.get(0);
                        double latitude = coordinates.get(1);
                        
                        // Validate coordinates (basic sanity check)
                        if (!isValidCoordinate(latitude, longitude)) {
                            log("ERROR: Invalid coordinates returned: " + latitude + ", " + longitude);
                            return null;
                        }
                        
                        // Construct formatted address from properties
                        String formattedAddress = constructFormattedAddress(properties);
                        
                        log("SELECTED RESULT: " + latitude + ", " + longitude + " - " + formattedAddress);
                        
                        GeocodingResult result = new GeocodingResult(latitude, longitude, formattedAddress);
                        
                        // Cache the result
                        geocodeCache.put(address, result);
                        
                        return result;
                    } else {
                        log("ERROR: No features found in response");
                    }
                } else {
                    log("ERROR: No 'features' key in response");
                }
            } else {
                log("ERROR: HTTP request failed with status " + response.getStatusCode());
            }
            
            // Try alternative address formats if the original fails
            log("FAILED to geocode address, trying alternative formats");
            GeocodingResult result = tryAlternativeAddressFormats(address);
            if (result != null) {
                // Cache the result with the original address
                geocodeCache.put(address, result);
                return result;
            }
            
            log("All geocoding attempts failed");
            return null;
        } catch (RestClientException e) {
            log("ERROR: API call failed - " + e.getMessage());
            return null;
        } catch (Exception e) {
            log("ERROR: Unexpected exception - " + e.getMessage());
            e.printStackTrace(logWriter);
            return null;
        }
    }
    
    /**
     * Try alternative address formats if the original format fails
     */
    private GeocodingResult tryAlternativeAddressFormats(String address) {
        // Try to parse the address into components
        String[] parts = address.split(",");
        
        // Simple case: just try without ZIP code
        if (address.matches(".*\\d{5}(-\\d{4})?$")) {
            String addressNoZip = address.replaceAll("\\s+\\d{5}(-\\d{4})?$", "");
            log("Trying without ZIP code: " + addressNoZip);
            
            try {
                GeocodingResult result = geocodeAddressDirect(addressNoZip);
                if (result != null) {
                    return result;
                }
            } catch (Exception e) {
                log("Error trying address without ZIP: " + e.getMessage());
            }
        }
        
        // If address has multiple parts (street, city, state, etc.)
        if (parts.length > 1) {
            // Try with just city and state
            if (parts.length >= 3) {
                String cityState = parts[parts.length - 2].trim() + ", " + parts[parts.length - 1].trim();
                log("Trying with just city and state: " + cityState);
                
                try {
                    GeocodingResult result = geocodeAddressDirect(cityState);
                    if (result != null) {
                        return result;
                    }
                } catch (Exception e) {
                    log("Error trying city and state: " + e.getMessage());
                }
            }
            
            // Try with just the street and city
            if (parts.length >= 2) {
                String streetCity = parts[0].trim() + ", " + parts[1].trim();
                log("Trying with just street and city: " + streetCity);
                
                try {
                    GeocodingResult result = geocodeAddressDirect(streetCity);
                    if (result != null) {
                        return result;
                    }
                } catch (Exception e) {
                    log("Error trying street and city: " + e.getMessage());
                }
            }
            
            // Try with just the first part (street)
            String street = parts[0].trim();
            if (street.length() > 3) {  // Ensure it's not too short
                log("Trying with just street: " + street);
                
                try {
                    GeocodingResult result = geocodeAddressDirect(street);
                    if (result != null) {
                        return result;
                    }
                } catch (Exception e) {
                    log("Error trying street only: " + e.getMessage());
                }
            }
        }
        
        return null;
    }
    
    /**
     * Direct geocoding without additional processing
     */
    private GeocodingResult geocodeAddressDirect(String address) {
        try {
            // Encode the address for URL
            String encodedAddress = URLEncoder.encode(address, StandardCharsets.UTF_8.toString());
            String url = String.format("%s/api?q=%s&limit=%d", photonUrl, encodedAddress, limit);
            
            log("DIRECT API REQUEST: " + url);
            
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Parse the JSON response
                Map<String, Object> jsonMap = mapper.readValue(response.getBody(), Map.class);
                
                if (jsonMap.containsKey("features")) {
                    List<Map<String, Object>> features = (List<Map<String, Object>>) jsonMap.get("features");
                    
                    if (!features.isEmpty()) {
                        // Use the first (best) result
                        Map<String, Object> feature = features.get(0);
                        Map<String, Object> geometry = (Map<String, Object>) feature.get("geometry");
                        List<Double> coordinates = (List<Double>) geometry.get("coordinates");
                        Map<String, Object> properties = (Map<String, Object>) feature.get("properties");
                        
                        // Photon returns coordinates as [lon, lat]
                        double longitude = coordinates.get(0);
                        double latitude = coordinates.get(1);
                        
                        // Validate coordinates
                        if (!isValidCoordinate(latitude, longitude)) {
                            log("ERROR: Invalid coordinates returned: " + latitude + ", " + longitude);
                            return null;
                        }
                        
                        // Construct formatted address from properties
                        String formattedAddress = constructFormattedAddress(properties);
                        
                        log("DIRECT SELECTED RESULT: " + latitude + ", " + longitude + " - " + formattedAddress);
                        
                        return new GeocodingResult(latitude, longitude, formattedAddress);
                    }
                }
            }
            
            return null;
        } catch (Exception e) {
            log("ERROR in direct geocoding: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Verify that coordinates are in valid ranges
     */
    private boolean isValidCoordinate(double lat, double lon) {
        // Check if coordinates are in valid ranges
        boolean valid = lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
        
        // Additional check: if coordinates are suspiciously close to 0,0 (null island)
        if (Math.abs(lat) < 0.001 && Math.abs(lon) < 0.001) {
            log("WARNING: Coordinates are suspiciously close to 0,0 (null island)");
            return false;
        }
        
        return valid;
    }
    
    /**
     * Convert coordinates back to an address
     * 
     * @param latitude The latitude
     * @param longitude The longitude
     * @return GeocodingResult representation of the address or null if not found
     */
    public GeocodingResult reverseGeocode(double latitude, double longitude) {
        log("\n==== Reverse geocoding: " + latitude + ", " + longitude + " ====");
        
        try {
            String url = String.format("%s/reverse?lat=%f&lon=%f", photonUrl, latitude, longitude);
            
            log("API REQUEST: " + url);
            
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            log("API STATUS: " + response.getStatusCode());
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Log raw response for debugging
                String responseBody = response.getBody();
                
                // Log only the first 1000 characters if too long
                if (responseBody.length() > 1000) {
                    log("API RESPONSE (truncated): " + responseBody.substring(0, 1000) + "...");
                } else {
                    log("API RESPONSE: " + responseBody);
                }
                
                // Parse the JSON response
                Map<String, Object> jsonMap = mapper.readValue(responseBody, Map.class);
                
                if (jsonMap.containsKey("features")) {
                    List<Map<String, Object>> features = (List<Map<String, Object>>) jsonMap.get("features");
                    
                    if (!features.isEmpty()) {
                        log("Found " + features.size() + " results");
                        
                        // Use the first (best) result
                        Map<String, Object> feature = features.get(0);
                        Map<String, Object> properties = (Map<String, Object>) feature.get("properties");
                        
                        // Construct formatted address from properties
                        String formattedAddress = constructFormattedAddress(properties);
                        
                        log("SELECTED RESULT: " + formattedAddress);
                        
                        return new GeocodingResult(latitude, longitude, formattedAddress);
                    } else {
                        log("ERROR: No features found in response");
                    }
                } else {
                    log("ERROR: No 'features' key in response");
                }
            } else {
                log("ERROR: HTTP request failed with status " + response.getStatusCode());
            }
            
            log("FAILED to reverse geocode coordinates");
            return null;
        } catch (RestClientException e) {
            log("ERROR: API call failed - " + e.getMessage());
            return null;
        } catch (Exception e) {
            log("ERROR: Unexpected exception - " + e.getMessage());
            e.printStackTrace(logWriter);
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
        
        String formattedAddress = String.join(", ", addressParts);
        log("Constructed formatted address: " + formattedAddress);
        
        return formattedAddress;
    }
    
    /**
     * Log a message to both console and file
     */
    private void log(String message) {
        try {
            System.out.println("[PHOTON] " + message);
            logWriter.println("[" + LocalDateTime.now() + "] " + message);
            logWriter.flush();
        } catch (Exception e) {
            System.err.println("Error writing to log: " + e.getMessage());
        }
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