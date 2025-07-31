package sandbox27.ila.backend.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.period.Period;

import java.util.Optional;

@Repository
public interface CourseBlockAssignmentRepository extends JpaRepository<CourseBlockAssignment, Long> {

    Optional<CourseBlockAssignment> findByPeriodAndBlockAndCourse(Period period, Block block, Course course);
}
