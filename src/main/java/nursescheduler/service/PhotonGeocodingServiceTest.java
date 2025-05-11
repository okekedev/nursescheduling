package nursescheduler.test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import nursescheduler.NurseSchedulerApplication;
import nursescheduler.service.PhotonGeocodingService;
import nursescheduler.service.PhotonGeocodingService.GeocodingResult;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Standalone test for PhotonGeocodingService
 * Run with: mvn spring-boot:run -Dspring-boot.run.profiles=photon-test
 */
@Component
@Profile("photon-test")
public class PhotonGeocodingServiceTest implements CommandLineRunner {

    @Autowired
    private PhotonGeocodingService geocodingService;
    
    @Autowired
    private ResourceLoader resourceLoader;
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    // For direct API testing
    private final RestTemplate restTemplate = new RestTemplate();
    
    // Log file
    private PrintWriter logWriter;
    
    public static void main(String[] args) {
        SpringApplication.run(NurseSchedulerApplication.class, 
            "--spring.profiles.active=photon-test");
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("========== PHOTON GEOCODING SERVICE TEST ==========");
        
        // Create log file
        logWriter = new PrintWriter(new FileWriter("photon-test-log.txt"));
        log("Starting Photon geocoding test at " + java.time.LocalDateTime.now());
        
        try {
            // Test the Photon service directly
            testPhotonServiceDirect();
            
            // Test with addresses from JSON files
            testWithJsonData();
            
            // Test various address formats
            testAddressFormats();
            
            log("All tests completed successfully");
        } catch (Exception e) {
            log("ERROR: Test failed with exception: " + e.getMessage());
            e.printStackTrace(logWriter);
        } finally {
            logWriter.close();
            System.out.println("Test completed. See photon-test-log.txt for detailed results.");
        }
    }
    
    /**
     * Test the Photon service directly with known addresses
     */
    private void testPhotonServiceDirect() {
        log("===== TESTING PHOTON SERVICE DIRECTLY =====");
        
        // Test known addresses
        List<String> testAddresses = new ArrayList<>();
        testAddresses.add("1600 Pennsylvania Ave, Washington, DC 20500"); // White House
        testAddresses.add("900 Ohio Dr SW, Washington, DC 20024"); // Lincoln Memorial
        testAddresses.add("123 Main St, Burkburnett, TX 76354"); // Generic address
        testAddresses.add("Burkburnett, TX"); // Just city and state
        testAddresses.add("Wichita Falls, TX 76301"); // City with ZIP
        
        // Test each address
        for (String address : testAddresses) {
            log("\nTesting address: " + address);
            
            // Test via our service
            try {
                GeocodingResult result = geocodingService.geocodeAddress(address);
                
                if (result != null) {
                    log("SUCCESS via service: " + result.getLatitude() + ", " + result.getLongitude());
                    log("Formatted address: " + result.getFormattedAddress());
                } else {
                    log("FAILED via service: No result returned");
                }
            } catch (Exception e) {
                log("ERROR via service: " + e.getMessage());
            }
            
            // Test direct API call to see raw response
            try {
                String encodedAddress = address.replace(" ", "+");
                String url = "http://localhost:2322/api?q=" + encodedAddress + "&limit=1";
                log("Calling API directly: " + url);
                
                String response = restTemplate.getForObject(url, String.class);
                log("Raw API response: " + response);
            } catch (Exception e) {
                log("ERROR in direct API call: " + e.getMessage());
            }
        }
    }
    
    /**
     * Test with addresses from JSON files
     */
    private void testWithJsonData() throws Exception {
        log("\n===== TESTING WITH JSON DATA =====");
        
        // Test with workers.json
        testWorkersJson();
        
        // Test with patients.json
        testPatientsJson();
    }
    
    /**
     * Test with workers.json data
     */
    private void testWorkersJson() throws Exception {
        log("\n----- Testing with workers.json -----");
        
        Resource workersResource = resourceLoader.getResource("classpath:static/JSON/workers.json");
        if (!workersResource.exists()) {
            log("ERROR: workers.json not found");
            return;
        }
        
        Map<String, Object> workersData = mapper.readValue(
            workersResource.getInputStream(), 
            new TypeReference<Map<String, Object>>() {}
        );
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> workers = (List<Map<String, Object>>) workersData.get("workers");
        
        log("Found " + workers.size() + " workers in JSON file");
        
        for (Map<String, Object> worker : workers) {
            String firstName = (String) worker.get("firstName");
            String lastName = (String) worker.get("lastName");
            String name = firstName + " " + lastName;
            
            @SuppressWarnings("unchecked")
            Map<String, Object> address = (Map<String, Object>) worker.get("address");
            
            if (address != null) {
                String street = (String) address.get("street");
                String city = (String) address.get("city");
                String state = (String) address.get("state");
                String zip = (String) address.get("zip");
                
                String fullAddress = street + ", " + city + ", " + state + " " + zip;
                log("\nTesting worker address: " + name + " - " + fullAddress);
                
                // Check for existing coordinates in JSON
                boolean hasCoordinatesInJson = false;
                @SuppressWarnings("unchecked")
                Map<String, Object> coordinates = (Map<String, Object>) worker.get("coordinates");
                
                if (coordinates != null && coordinates.containsKey("latitude") && coordinates.containsKey("longitude")) {
                    Double lat = convertToDouble(coordinates.get("latitude"));
                    Double lng = convertToDouble(coordinates.get("longitude"));
                    
                    if (lat != null && lng != null && (Math.abs(lat) > 0.001 || Math.abs(lng) > 0.001)) {
                        log("Worker has coordinates in JSON: " + lat + ", " + lng);
                        hasCoordinatesInJson = true;
                    }
                }
                
                if (!hasCoordinatesInJson) {
                    log("Worker has no coordinates in JSON, geocoding...");
                }
                
                try {
                    // Try geocoding with different address formats
                    testAddressVariants(name, street, city, state, zip);
                } catch (Exception e) {
                    log("ERROR geocoding worker address: " + e.getMessage());
                }
            } else {
                log("\nWARNING: Worker " + name + " has no address information");
            }
        }
    }
    
    /**
     * Test with patients.json data
     */
    private void testPatientsJson() throws Exception {
        log("\n----- Testing with patients.json -----");
        
        Resource patientsResource = resourceLoader.getResource("classpath:static/JSON/patients.json");
        if (!patientsResource.exists()) {
            log("ERROR: patients.json not found");
            return;
        }
        
        Map<String, Object> patientsData = mapper.readValue(
            patientsResource.getInputStream(),
            new TypeReference<Map<String, Object>>() {}
        );
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> patients = (List<Map<String, Object>>) patientsData.get("patients");
        
        log("Found " + patients.size() + " patients in JSON file");
        
        for (Map<String, Object> patient : patients) {
            String firstName = (String) patient.get("firstName");
            String lastName = (String) patient.get("lastName");
            String name = firstName + " " + lastName;
            
            String street = (String) patient.get("street");
            String city = (String) patient.get("city");
            String state = (String) patient.get("state");
            String zip = (String) patient.get("zip");
            
            if (street != null && city != null && state != null) {
                String fullAddress = street + ", " + city + ", " + state;
                if (zip != null) {
                    fullAddress += " " + zip;
                }
                
                log("\nTesting patient address: " + name + " - " + fullAddress);
                
                // Check for existing coordinates in JSON
                boolean hasCoordinatesInJson = false;
                @SuppressWarnings("unchecked")
                Map<String, Object> coordinates = (Map<String, Object>) patient.get("coordinates");
                
                if (coordinates != null && coordinates.containsKey("latitude") && coordinates.containsKey("longitude")) {
                    Double lat = convertToDouble(coordinates.get("latitude"));
                    Double lng = convertToDouble(coordinates.get("longitude"));
                    
                    if (lat != null && lng != null && (Math.abs(lat) > 0.001 || Math.abs(lng) > 0.001)) {
                        log("Patient has coordinates in JSON: " + lat + ", " + lng);
                        hasCoordinatesInJson = true;
                    }
                }
                
                if (!hasCoordinatesInJson) {
                    log("Patient has no coordinates in JSON, geocoding...");
                }
                
                try {
                    // Try geocoding with different address formats
                    testAddressVariants(name, street, city, state, zip);
                } catch (Exception e) {
                    log("ERROR geocoding patient address: " + e.getMessage());
                }
            } else {
                log("\nWARNING: Patient " + name + " has incomplete address information");
            }
        }
    }
    
    /**
     * Test different address formats and variants
     */
    private void testAddressVariants(String name, String street, String city, String state, String zip) {
        // Full address
        String fullAddress = street + ", " + city + ", " + state + " " + zip;
        testGeocode("Full address", fullAddress);
        
        // Without ZIP
        String addressNoZip = street + ", " + city + ", " + state;
        testGeocode("Without ZIP", addressNoZip);
        
        // Just street and city
        String streetCity = street + ", " + city;
        testGeocode("Street and city", streetCity);
        
        // Just city and state
        String cityState = city + ", " + state;
        testGeocode("City and state", cityState);
        
        // Without street number (if applicable)
        if (street != null && street.matches("\\d+.*")) {
            String streetNoNumber = street.replaceAll("^\\d+\\s*", "");
            String addressNoNumber = streetNoNumber + ", " + city + ", " + state + " " + zip;
            testGeocode("Without street number", addressNoNumber);
        }
    }
    
    /**
     * Test address formats with variations
     */
    private void testAddressFormats() {
        log("\n===== TESTING ADDRESS FORMAT VARIANTS =====");
        
        // Base address
        String baseStreet = "123 Main St";
        String baseCity = "Burkburnett";
        String baseState = "TX";
        String baseZip = "76354";
        
        log("\nUsing base address: " + baseStreet + ", " + baseCity + ", " + baseState + " " + baseZip);
        
        // Test format variations
        String[] formats = {
            "{street}, {city}, {state} {zip}",
            "{street} {city} {state} {zip}",
            "{street}, {city} {state}",
            "{street}, {city}, {state}",
            "{city}, {state}",
            "{street}, {zip}",
            "{street}",
            "{city}"
        };
        
        for (String format : formats) {
            String address = format
                .replace("{street}", baseStreet)
                .replace("{city}", baseCity)
                .replace("{state}", baseState)
                .replace("{zip}", baseZip);
                
            log("\nTesting format: " + format);
            log("Formatted as: " + address);
            
            try {
                GeocodingResult result = geocodingService.geocodeAddress(address);
                
                if (result != null) {
                    log("SUCCESS: " + result.getLatitude() + ", " + result.getLongitude());
                    log("Formatted address: " + result.getFormattedAddress());
                    
                    // Test direct API with the same address to see raw response
                    String encodedAddress = address.replace(" ", "+");
                    String url = "http://localhost:2322/api?q=" + encodedAddress + "&limit=1";
                    String response = restTemplate.getForObject(url, String.class);
                    log("Raw API response: " + response);
                } else {
                    log("FAILED: No result returned");
                }
            } catch (Exception e) {
                log("ERROR: " + e.getMessage());
            }
        }
    }
    
    /**
     * Test a specific address with the geocoding service
     */
    private void testGeocode(String label, String address) {
        log("\n  Testing " + label + ": " + address);
        
        try {
            GeocodingResult result = geocodingService.geocodeAddress(address);
            
            if (result != null) {
                log("  SUCCESS: " + result.getLatitude() + ", " + result.getLongitude());
                log("  Formatted address: " + result.getFormattedAddress());
            } else {
                log("  FAILED: No result returned");
            }
        } catch (Exception e) {
            log("  ERROR: " + e.getMessage());
        }
    }
    
    /**
     * Log a message to both console and file
     */
    private void log(String message) {
        System.out.println(message);
        logWriter.println(message);
        logWriter.flush();
    }
    
    /**
     * Helper for converting JSON values to Double
     */
    private Double convertToDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}