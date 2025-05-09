package nursescheduler.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import nursescheduler.model.Patient;

import java.util.List;

/**
 * Repository for accessing and managing Patient entities
 */
@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {
    // JpaRepository provides basic CRUD operations
    
    // Find patient by name
    Patient findByName(String name);
    
    // Find patients by city
    List<Patient> findByCity(String city);
    
    // Find patients by state
    List<Patient> findByState(String state);
}