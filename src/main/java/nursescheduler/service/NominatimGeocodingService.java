package nursescheduler.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for geocoding addresses to coordinates using a local Nominatim instance
 * Maintains HIPAA compliance by not sending any data externally
 */
@Service
public class NominatimGeocodingService {

    @Value("${geocoding.nominatim.url:http://localhost:8080}")
    private String nominatimUrl;

    @Value("${geocoding.nominatim.limit:5}")
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
            // Build URL for Nominatim search
            String url = UriComponentsBuilder.fromHttpUrl(nominatimUrl + "/search")
                .queryParam("q", address)
                .queryParam("format", "json")
                .queryParam("limit", limit)
                .queryParam("addressdetails", 1)
                .build()
                .toUriString();
            
            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            List<Map<String, Object>> results = response.getBody();
            
            if (results != null && !results.isEmpty()) {
                Map<String, Object> result = results.get(0);
                
                double latitude = Double.parseDouble((String) result.get("lat"));
                double longitude = Double.parseDouble((String) result.get("lon"));
                String displayName = (String) result.get("display_name");
                
                GeocodingResult geocodingResult = new GeocodingResult(latitude, longitude, displayName);
                
                // Cache the result
                geocodeCache.put(address, geocodingResult);
                
                return geocodingResult;
            }
            
            return null;
        } catch (Exception e) {
            System.err.println("Geocoding error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Convert coordinates back to an address
     * 
     * @param latitude The latitude
     * @param longitude The longitude
     * @return GeocodingResult representation of the address or null if not found
     */
    public GeocodingResult reverseGeocode(double latitude, double longitude) {
        try {
            // Build URL for Nominatim reverse geocoding
            String url = UriComponentsBuilder.fromHttpUrl(nominatimUrl + "/reverse")
                .queryParam("lat", latitude)
                .queryParam("lon", longitude)
                .queryParam("format", "json")
                .queryParam("addressdetails", 1)
                .build()
                .toUriString();
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> result = response.getBody();
            
            if (result != null && result.containsKey("display_name")) {
                String displayName = (String) result.get("display_name");
                return new GeocodingResult(latitude, longitude, displayName);
            }
            
            return null;
        } catch (Exception e) {
            System.err.println("Reverse geocoding error: " + e.getMessage());
            e.printStackTrace();
            return null;
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