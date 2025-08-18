package sandbox27.ila.backend.assignements;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseUserAssignmentRepository extends JpaRepository<CourseUserAssignment, Long> {
}
