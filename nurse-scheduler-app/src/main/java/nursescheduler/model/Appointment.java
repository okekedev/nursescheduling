package nursescheduler.model;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class Appointment {
    @Id
    private String visitId;
    private String practitionerId;
    private String patientId;
    private String locationId;
    private LocalDateTime startTime;
    private Integer duration;
    private String status;
    private String serviceType;
    private String detailedStatus;

    // Getters and setters
    public String getVisitId() { return visitId; }
    public void setVisitId(String visitId) { this.visitId = visitId; }
    public String getPractitionerId() { return practitionerId; }
    public void setPractitionerId(String practitionerId) { this.practitionerId = practitionerId; }
    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
    public String getLocationId() { return locationId; }
    public void setLocationId(String locationId) { this.locationId = locationId; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public String getDetailedStatus() { return detailedStatus; }
    public void setDetailedStatus(String detailedStatus) { this.detailedStatus = detailedStatus; }
}