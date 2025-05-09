package nursescheduler.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import nursescheduler.model.NurseSchedule;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for accessing and managing NurseSchedule entities
 */
@Repository
public interface NurseScheduleRepository extends JpaRepository<NurseSchedule, Long> {
    
    // Find schedules by nurse ID
    List<NurseSchedule> findByNurseId(String nurseId);
    
    // Find schedules by date
    List<NurseSchedule> findByScheduleDate(LocalDate scheduleDate);
    
    // Find schedules by nurse ID and date
    NurseSchedule findByNurseIdAndScheduleDate(String nurseId, LocalDate scheduleDate);
    
    // Find schedules by status
    List<NurseSchedule> findByStatus(String status);
    
    // Find schedules generated on a specific date
    List<NurseSchedule> findByGeneratedDate(LocalDate generatedDate);
    
    // Find schedules for a nurse within a date range
    List<NurseSchedule> findByNurseIdAndScheduleDateBetween(String nurseId, LocalDate startDate, LocalDate endDate);
}