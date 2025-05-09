package nursescheduler.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import nursescheduler.model.NurseSchedule;
import nursescheduler.service.NurseScheduleService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for nurse schedule operations
 */
@RestController
@RequestMapping("/api/schedules")
public class NurseScheduleController {

    @Autowired
    private NurseScheduleService nurseScheduleService;

    /**
     * Get or generate a schedule for a nurse on a specific date
     */
    @GetMapping("/{nurseId}")
    public Map<String, Object> getSchedule(
            @PathVariable String nurseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            NurseSchedule schedule = nurseScheduleService.getOrGenerateSchedule(nurseId, date);
            response.put("success", true);
            response.put("schedule", schedule);
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }
    
    /**
     * Get schedules for a nurse within a date range
     */
    @GetMapping("/{nurseId}/range")
    public Map<String, Object> getSchedulesForRange(
            @PathVariable String nurseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<NurseSchedule> schedules = nurseScheduleService.getSchedulesForNurse(nurseId, startDate, endDate);
            response.put("success", true);
            response.put("schedules", schedules);
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }
    
    /**
     * Get all schedules for a specific date
     */
    @GetMapping("/date")
    public Map<String, Object> getSchedulesForDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<NurseSchedule> schedules = nurseScheduleService.getSchedulesForDate(date);
            response.put("success", true);
            response.put("schedules", schedules);
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }
    
    /**
     * Generate a new schedule for a nurse on a specific date
     * (forces regeneration even if a schedule already exists)
     */
    @PostMapping("/{nurseId}/generate")
    public Map<String, Object> generateSchedule(
            @PathVariable String nurseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            NurseSchedule schedule = nurseScheduleService.generateSchedule(nurseId, date);
            response.put("success", true);
            response.put("schedule", schedule);
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }
    
    /**
     * Update the status of a schedule
     */
    @PutMapping("/{scheduleId}/status")
    public Map<String, Object> updateScheduleStatus(
            @PathVariable Long scheduleId,
            @RequestBody Map<String, String> requestBody) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            String newStatus = requestBody.get("status");
            if (newStatus == null || newStatus.isEmpty()) {
                throw new IllegalArgumentException("Status is required");
            }
            
            NurseSchedule updatedSchedule = nurseScheduleService.updateScheduleStatus(scheduleId, newStatus);
            response.put("success", true);
            response.put("schedule", updatedSchedule);
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }
}