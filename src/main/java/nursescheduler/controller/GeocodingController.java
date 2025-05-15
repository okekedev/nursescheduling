package nursescheduler.controller;

import nursescheduler.service.NominatimGeocodingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for geocoding operations
 * Provides endpoints for converting addresses to coordinates and vice versa
 */
@RestController
@RequestMapping("/api")
public class GeocodingController {

    @Autowired
    private NominatimGeocodingService geocodingService;

    /**
     * Geocode an address to latitude/longitude
     * 
     * @param request Map containing the address to geocode
     * @return Map containing the geocoding result or error
     */
    @PostMapping("/geocode")
    public Map<String, Object> geocodeAddress(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String address = request.get("address");
            
            if (address == null || address.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "Address is required");
                return response;
            }
            
            NominatimGeocodingService.GeocodingResult result = geocodingService.geocodeAddress(address);
            
            if (result != null) {
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("latitude", result.getLatitude());
                resultMap.put("longitude", result.getLongitude());
                resultMap.put("formattedAddress", result.getFormattedAddress());
                
                response.put("success", true);
                response.put("result", resultMap);
            } else {
                response.put("success", false);
                response.put("error", "Address not found");
            }
            
            return response;
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }
    
    /**
     * Reverse geocode coordinates to an address
     * 
     * @param request Map containing the latitude and longitude to reverse geocode
     * @return Map containing the reverse geocoding result or error
     */
    @PostMapping("/reverse-geocode")
    public Map<String, Object> reverseGeocode(@RequestBody Map<String, Double> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Double latitude = request.get("latitude");
            Double longitude = request.get("longitude");
            
            if (latitude == null || longitude == null) {
                response.put("success", false);
                response.put("error", "Latitude and longitude are required");
                return response;
            }
            
            NominatimGeocodingService.GeocodingResult result = geocodingService.reverseGeocode(latitude, longitude);
            
            if (result != null) {
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("latitude", result.getLatitude());
                resultMap.put("longitude", result.getLongitude());
                resultMap.put("formattedAddress", result.getFormattedAddress());
                
                response.put("success", true);
                response.put("result", resultMap);
            } else {
                response.put("success", false);
                response.put("error", "No address found for these coordinates");
            }
            
            return response;
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }
}