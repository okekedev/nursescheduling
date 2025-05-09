package nursescheduler.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import nursescheduler.model.Nurse;

/**
 * Repository for accessing and managing Nurse entities
 */
@Repository
public interface NurseRepository extends JpaRepository<Nurse, Long> {
    // JpaRepository provides basic CRUD operations
    
    // Find nurse by name
    Nurse findByName(String name);
}