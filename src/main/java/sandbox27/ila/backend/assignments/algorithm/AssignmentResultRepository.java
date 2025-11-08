package sandbox27.ila.backend.assignments.algorithm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssignmentResultRepository extends JpaRepository<AssignmentResult, Long> {

    List<AssignmentResult> findByPeriod_IdOrderByExecutedAtDesc(long periodId);

    Optional<AssignmentResult> findFirstByPeriod_IdOrderByExecutedAtDesc(long periodId);
}