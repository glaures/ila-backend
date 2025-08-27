package sandbox27.ila.backend.assignements;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.user.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseUserAssignmentRepository extends JpaRepository<CourseUserAssignment, Long> {

    Optional<CourseUserAssignment> findByUserAndBlock_Id(User user, Long blockId);

    Optional<CourseUserAssignment> findByCourseAndUser(Course course, User user);

    List<CourseUserAssignment> findByUserAndBlock_Period(User user, Period period);

    List<CourseUserAssignment> findByCourse_Period(Period currentPeriod);

    List<CourseUserAssignment> findByCourse_id(Long courseId);

    int deleteAllByPreset(boolean b);
}
