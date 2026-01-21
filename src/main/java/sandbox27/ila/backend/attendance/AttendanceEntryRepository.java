package sandbox27.ila.backend.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceEntryRepository extends JpaRepository<AttendanceEntry, Long> {

    @Query("SELECT e FROM AttendanceEntry e WHERE e.session.id = :sessionId")
    List<AttendanceEntry> findBySessionId(@Param("sessionId") Long sessionId);

    @Query("SELECT e FROM AttendanceEntry e WHERE e.session.id = :sessionId AND e.user.userName = :userName")
    Optional<AttendanceEntry> findBySessionIdAndUserName(@Param("sessionId") Long sessionId, @Param("userName") String userName);

    void deleteBySessionId(Long sessionId);
}
