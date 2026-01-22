package sandbox27.ila.backend.courseexclusions;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.user.User;

import java.util.List;
import java.util.Optional;

public interface CourseExclusionRepository extends JpaRepository<CourseExclusion, Long> {

    List<CourseExclusion> findAllByCourse(Course course);

    List<CourseExclusion> findAllByCourseId(Long courseId);

    List<CourseExclusion> findAllByUser(User user);

    Optional<CourseExclusion> findByCourseAndUser(Course course, User user);

    boolean existsByCourseAndUser(Course course, User user);

    void deleteByCourse(Course course);

    void deleteByCourseId(Long courseId);

    void deleteAllByCourseId(Long courseId);

    void deleteByCourseAndUser(Course course, User user);

    @Query("SELECT ce FROM CourseExclusion ce WHERE ce.course.id = :courseId AND ce.user.userName = :userName")
    Optional<CourseExclusion> findByCourseIdAndUserName(@Param("courseId") Long courseId, @Param("userName") String userName);

    @Query("SELECT ce FROM CourseExclusion ce JOIN ce.course c WHERE c.period.id = :periodId")
    List<CourseExclusion> findAllByPeriodId(@Param("periodId") Long periodId);

    @Query("SELECT ce FROM CourseExclusion ce JOIN ce.course c WHERE c.period.id = :periodId AND ce.user.userName = :userName")
    List<CourseExclusion> findAllByPeriodIdAndUserName(@Param("periodId") Long periodId, @Param("userName") String userName);
}