package nursescheduler.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.OrderColumn;
import java.time.LocalDate;
import java.util.List;

/**
 * Entity class representing a daily schedule for a nurse
 * Includes optimized route information and visit order
 */
@Entity
@Table(name = "nurse_schedule")
public class NurseSchedule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String nurseId;
    private LocalDate scheduleDate;
    
    // Total distance of the route in meters
    private double totalDistance;
    
    // Estimated total travel time in minutes
    private int totalTravelTime;
    
    // Ordered list of patient IDs in optimal visit sequence
    @ElementCollection
    @OrderColumn
    @Column(name = "patient_id")
    private List<String> patientVisitOrder;
    
    // Route coordinates as JSON string (latitude,longitude pairs)
    @Column(columnDefinition = "TEXT")
    private String routeCoordinates;
    
    // Status of the schedule (DRAFT, CONFIRMED, IN_PROGRESS, COMPLETED)
    private String status;
    
    // When this schedule was generated/last updated
    private LocalDate generatedDate;
    
    // Getters and setters
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getNurseId() {
        return nurseId;
    }
    
    public void setNurseId(String nurseId) {
        this.nurseId = nurseId;
    }
    
    public LocalDate getScheduleDate() {
        return scheduleDate;
    }
    
    public void setScheduleDate(LocalDate scheduleDate) {
        this.scheduleDate = scheduleDate;
    }
    
    public double getTotalDistance() {
        return totalDistance;
    }
    
    public void setTotalDistance(double totalDistance) {
        this.totalDistance = totalDistance;
    }
    
    public int getTotalTravelTime() {
        return totalTravelTime;
    }
    
    public void setTotalTravelTime(int totalTravelTime) {
        this.totalTravelTime = totalTravelTime;
    }
    
    public List<String> getPatientVisitOrder() {
        return patientVisitOrder;
    }
    
    public void setPatientVisitOrder(List<String> patientVisitOrder) {
        this.patientVisitOrder = patientVisitOrder;
    }
    
    public String getRouteCoordinates() {
        return routeCoordinates;
    }
    
    public void setRouteCoordinates(String routeCoordinates) {
        this.routeCoordinates = routeCoordinates;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public LocalDate getGeneratedDate() {
        return generatedDate;
    }
    
    public void setGeneratedDate(LocalDate generatedDate) {
        this.generatedDate = generatedDate;
    }
}