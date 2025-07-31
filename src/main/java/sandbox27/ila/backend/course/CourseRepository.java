package sandbox27.ila.backend.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findByName(String name);

    List<Course> findAllByPeriod_Id(long periodId);
    long countByPeriod_Id(long periodId);

    @Query("select cba.course from CourseBlockAssignment cba " +
            "where cba.block.dayOfWeek=:dayOfWeek " +
            "and cba.period.id=:periodId " +
            "order by cba.block.startTime asc")
    List<Course> findAllByPeriod_IdAndDayOfWeek(long periodId, DayOfWeek dayOfWeek);

    @Query("select cba.course from CourseBlockAssignment cba " +
            "where cba.block.id=:blockId")
    List<Course> findAllByBlock_Id(Long blockId);
}
