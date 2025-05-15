package nursescheduler.controller;

import nursescheduler.service.NominatimGeocodingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for testing external services
 */
@RestController
@RequestMapping("/api/test")
public class TestController {

    @Autowired
    private NominatimGeocodingService geocodingService;

    /**
     * Test endpoint for Nominatim geocoding
     */
    @GetMapping("/geocode")
    public Map<String, Object> testGeocode(@RequestParam String address) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            NominatimGeocodingService.GeocodingResult result = geocodingService.geocodeAddress(address);
            
            if (result != null) {
                response.put("success", true);
                response.put("latitude", result.getLatitude());
                response.put("longitude", result.getLongitude());
                response.put("formattedAddress", result.getFormattedAddress());
            } else {
                response.put("success", false);
                response.put("error", "Address not found");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return response;
    }

    /**
     * Test endpoint for Nominatim reverse geocoding
     */
    @GetMapping("/reverse-geocode")
    public Map<String, Object> testReverseGeocode(
            @RequestParam double latitude, 
            @RequestParam double longitude) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            NominatimGeocodingService.GeocodingResult result = 
                    geocodingService.reverseGeocode(latitude, longitude);
            
            if (result != null) {
                response.put("success", true);
                response.put("latitude", result.getLatitude());
                response.put("longitude", result.getLongitude());
                response.put("formattedAddress", result.getFormattedAddress());
            } else {
                response.put("success", false);
                response.put("error", "No address found for these coordinates");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return response;
    }
}