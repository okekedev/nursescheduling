package nursescheduler.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import nursescheduler.service.NurseScheduleService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for initializing nurse schedules
 */
@RestController
@RequestMapping("/api/init")
public class ScheduleInitializerController {

    @Autowired
    private NurseScheduleService nurseScheduleService;
    
    /**
     * Generate schedules for all nurses for today
     */
    @GetMapping("/schedules/today")
    public Map<String, Object> generateSchedulesForToday() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            LocalDate today = LocalDate.now();
            nurseScheduleService.generateSchedulesForAllNurses(today);
            
            response.put("success", true);
            response.put("message", "Generated schedules for all nurses for today (" + today + ")");
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }
    
    /**
     * Generate schedules for all nurses for a specific date
     */
    @GetMapping("/schedules")
    public Map<String, Object> generateSchedulesForDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            nurseScheduleService.generateSchedulesForAllNurses(date);
            
            response.put("success", true);
            response.put("message", "Generated schedules for all nurses for " + date);
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }
}