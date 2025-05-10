package nursescheduler.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import nursescheduler.model.Nurse;
import nursescheduler.model.Patient;
import nursescheduler.repository.NurseRepository;
import nursescheduler.repository.PatientRepository;
import nursescheduler.service.PhotonGeocodingService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fix")
public class GeocodeFixController {

    @Autowired
    private NurseRepository nurseRepository;
    
    @Autowired
    private PatientRepository patientRepository;
    
    @Autowired
    private PhotonGeocodingService geocodingService;
    
    @GetMapping("/geocode-all")
    public Map<String, Object> fixAllCoordinates() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            int updatedNurses = fixNurseCoordinates();
            int updatedPatients = fixPatientCoordinates();
            
            response.put("success", true);
            response.put("message", "Geocoding process completed");
            response.put("updatedNurses", updatedNurses);
            response.put("updatedPatients", updatedPatients);
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }
    
    private int fixNurseCoordinates() {
        List<Nurse> nurses = nurseRepository.findAll();
        int updatedCount = 0;
        
        System.out.println("Starting to fix coordinates for " + nurses.size() + " nurses...");
        
        for (Nurse nurse : nurses) {
            // Skip nurses that already have valid coordinates
            if (nurse.getLatitude() != null && nurse.getLongitude() != null && 
                (Math.abs(nurse.getLatitude()) > 0.001 || Math.abs(nurse.getLongitude()) > 0.001)) {
                continue;
            }
            
            // Fetch nurse from workers.json and get address
            // For now, we'll use a mock approach assuming the address is available
            String mockAddress = "123 Main St, Wichita Falls, TX 76301"; // Replace with actual logic
            
            PhotonGeocodingService.GeocodingResult result = geocodingService.geocodeAddress(mockAddress);
            if (result != null) {
                nurse.setLatitude(result.getLatitude());
                nurse.setLongitude(result.getLongitude());
                nurseRepository.save(nurse);
                updatedCount++;
                System.out.println("Updated coordinates for nurse " + nurse.getName() + 
                                 ": " + result.getLatitude() + ", " + result.getLongitude());
            }
        }
        
        System.out.println("Updated coordinates for " + updatedCount + " nurses");
        return updatedCount;
    }
    
    private int fixPatientCoordinates() {
        // Similar implementation for patients
        return 0; // Placeholder
    }
}