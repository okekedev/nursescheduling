package nursescheduler.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import nursescheduler.model.Appointment;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for accessing and managing Appointment entities
 */
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, String> {
    // JpaRepository provides basic CRUD operations
    
    // Find appointments by patient ID
    List<Appointment> findByPatientId(String patientId);
    
    // Find appointments by practitioner (nurse) ID
    List<Appointment> findByPractitionerId(String practitionerId);
    
    // Find appointments for a specific date range
    List<Appointment> findByAppointmentDateBetween(LocalDateTime start, LocalDateTime end);
    
    // Find appointments by practitioner ID and date
    List<Appointment> findByPractitionerIdAndAppointmentDateBetween(String practitionerId, LocalDateTime start, LocalDateTime end);
}