package sandbox27.ila.backend.preference;

import jakarta.persistence.OrderBy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseService;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.user.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface PreferenceRepository extends JpaRepository<Preference, Long> {

    List<Preference> findAllByBlock_Period(Period period);

    List<Preference> findByUserAndBlock_IdOrderByPreferenceIndex(User user, Long blockId);

    void deleteByUserAndBlock(User user, Block block);

    @Query("select p.course.id from Preference p where p.block.period.id=:periodId and p.user=:user")
    List<String> findAllPreferencesCourseIdsByUserAndPeriod_Id(@Param("user") User user, @Param("periodId") long periodId);

    List<Preference> findPreferencesByUserAndBlockOrderByPreferenceIndex(User user, Block block);

    List<Preference> findByUserAndBlock_Period(User user, Period period);

    void deleteByCourse(Course course);

    @Query("SELECT COUNT(DISTINCT p.user) FROM Preference p WHERE p.block.period = :period")
    long countDistinctUsersByPeriod(@Param("period") Period period);

    @Query("SELECT p.course, COUNT(p) as cnt FROM Preference p " +
            "WHERE p.preferenceIndex = 0 AND p.course.period = :period " +
            "GROUP BY p.course " +
            "ORDER BY COUNT(p) DESC")
    List<Object[]> findTopCoursesByFirstPreferenceAndPeriod(@Param("period") Period period,
                                                            Pageable pageable);
    @Query("SELECT p.course, COUNT(p) as cnt FROM Preference p " +
            "WHERE p.preferenceIndex = 0 AND p.course.period = :period AND p.block.id=:blockId " +
            "GROUP BY p.course " +
            "ORDER BY COUNT(p) DESC")
    List<Object[]> findTopCoursesByFirstPreferenceAndPeriodAndBlock(@Param("period") Period period,
                                                            @Param("blockId") Long blockId,
                                                            Pageable pageable);

    Optional<Preference> findByUserAndBlock_IdAndCourse_Id(User user, long id, long id1);
}
