package sandbox27.ila.backend.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sandbox27.ila.backend.block.Block;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseBlockAssignmentRepository extends JpaRepository<CourseBlockAssignment, Long> {

    Optional<CourseBlockAssignment> findByBlockAndCourse(Block block, Course course);

    @Query("select cba from CourseBlockAssignment cba where cba.block.period.id=:periodId")
    List<CourseBlockAssignment> findAllByPeriodId(Long periodId);
}
