package sandbox27.ila.backend.exchange;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.user.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExchangeRequestRepository extends JpaRepository<ExchangeRequest, Long> {

    List<ExchangeRequest> findByPeriod(Period period);

    List<ExchangeRequest> findByPeriodAndStatus(Period period, ExchangeRequestStatus status);

    List<ExchangeRequest> findByStudentAndPeriod(User student, Period period);

    List<ExchangeRequest> findByStudentAndPeriodAndStatus(User student, Period period, ExchangeRequestStatus status);

    @Query("SELECT er FROM ExchangeRequest er " +
            "JOIN FETCH er.desiredCourses " +
            "WHERE er.period = :period AND er.status = :status")
    List<ExchangeRequest> findByPeriodAndStatusWithOptions(
            @Param("period") Period period,
            @Param("status") ExchangeRequestStatus status);

    @Query("SELECT er FROM ExchangeRequest er " +
            "JOIN FETCH er.currentAssignment " +
            "JOIN FETCH er.desiredCourses dc " +
            "JOIN FETCH dc.desiredCourse " +
            "WHERE er.period = :period AND er.status = 'PENDING'")
    List<ExchangeRequest> findPendingRequestsWithDetails(@Param("period") Period period);

    /**
     * Prüft, ob ein Schüler bereits einen offenen Wechselwunsch für eine bestimmte Zuweisung hat
     */
    @Query("SELECT COUNT(er) > 0 FROM ExchangeRequest er " +
            "WHERE er.student = :student " +
            "AND er.currentAssignment.id = :assignmentId " +
            "AND er.status = 'PENDING'")
    boolean existsPendingRequestForAssignment(
            @Param("student") User student,
            @Param("assignmentId") Long assignmentId);

    /**
     * Zählt offene Wechselwünsche eines Schülers in einer Periode
     */
    @Query("SELECT COUNT(er) FROM ExchangeRequest er " +
            "WHERE er.student = :student " +
            "AND er.period = :period " +
            "AND er.status = 'PENDING'")
    long countPendingRequestsByStudentAndPeriod(
            @Param("student") User student,
            @Param("period") Period period);

    /**
     * Findet alle Requests, die einen bestimmten Kurs als Wunsch haben
     */
    @Query("SELECT DISTINCT er FROM ExchangeRequest er " +
            "JOIN er.desiredCourses dc " +
            "WHERE dc.desiredCourse.id = :courseId " +
            "AND er.status = 'PENDING'")
    List<ExchangeRequest> findPendingRequestsDesiring(@Param("courseId") Long courseId);
}