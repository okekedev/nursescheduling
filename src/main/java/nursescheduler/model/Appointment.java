package nursescheduler.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Entity class representing an appointment between a nurse and a patient
 */
@Entity
@Table(name = "appointment")
public class Appointment {
    
    @Id
    private String appointmentId;
    
    private String patientId;
    private String practitionerId;
    private LocalDateTime appointmentDate;
    private String visitType;
    private String serviceCode;
    
    // Getters and setters
    
    public String getAppointmentId() {
        return appointmentId;
    }
    
    public void setAppointmentId(String appointmentId) {
        this.appointmentId = appointmentId;
    }
    
    public String getPatientId() {
        return patientId;
    }
    
    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }
    
    public String getPractitionerId() {
        return practitionerId;
    }
    
    public void setPractitionerId(String practitionerId) {
        this.practitionerId = practitionerId;
    }
    
    public LocalDateTime getAppointmentDate() {
        return appointmentDate;
    }
    
    public void setAppointmentDate(LocalDateTime appointmentDate) {
        this.appointmentDate = appointmentDate;
    }
    
    public String getVisitType() {
        return visitType;
    }
    
    public void setVisitType(String visitType) {
        this.visitType = visitType;
    }
    
    public String getServiceCode() {
        return serviceCode;
    }
    
    public void setServiceCode(String serviceCode) {
        this.serviceCode = serviceCode;
    }
}