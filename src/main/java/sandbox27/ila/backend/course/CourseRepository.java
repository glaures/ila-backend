package sandbox27.ila.backend.course;

import jakarta.persistence.OrderBy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import sandbox27.ila.backend.period.Period;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findByName(String name);

    @OrderBy("name")
    List<Course> findAllByPeriod_Id(long periodId);
    long countByPeriod_Id(long periodId);

    @Query("select cba.course from CourseBlockAssignment cba " +
            "where cba.block.dayOfWeek=:dayOfWeek " +
            "and cba.block.period.id=:periodId " +
            "order by cba.block.startTime asc")
    List<Course> findAllByPeriod_IdAndDayOfWeek(long periodId, DayOfWeek dayOfWeek);

    @OrderBy("name")
    List<Course> findAllByPeriod(Period period);

    @OrderBy("name")
    @Query("select cba.course from CourseBlockAssignment cba " +
            "where cba.block.id=:blockId order by cba.course.courseId")
    List<Course> findAllByBlock_Id(Long blockId);

    Optional<Course> findByCourseId(String s);

    Course getReferenceByCourseId(String courseId);
}
