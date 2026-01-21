package sandbox27.ila.backend.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {

    @Query("SELECT s FROM AttendanceSession s WHERE s.course.id = :courseId ORDER BY s.date DESC")
    List<AttendanceSession> findByCourseIdOrderByDateDesc(@Param("courseId") Long courseId);

    @Query("SELECT s FROM AttendanceSession s WHERE s.course.id = :courseId AND s.date = :date")
    Optional<AttendanceSession> findByCourseIdAndDate(@Param("courseId") Long courseId, @Param("date") LocalDate date);

    @Query("SELECT s FROM AttendanceSession s LEFT JOIN FETCH s.entries WHERE s.id = :id")
    Optional<AttendanceSession> findByIdWithEntries(@Param("id") Long id);
}
