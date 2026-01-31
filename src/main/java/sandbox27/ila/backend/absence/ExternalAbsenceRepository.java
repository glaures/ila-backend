package sandbox27.ila.backend.absence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExternalAbsenceRepository extends JpaRepository<ExternalAbsence, Long> {

    /**
     * Findet eine Abwesenheit anhand der externen ID aus Beste.Schule
     */
    Optional<ExternalAbsence> findByExternalId(Long externalId);

    /**
     * Findet alle Abwesenheiten für ein bestimmtes Datum
     */
    List<ExternalAbsence> findByDate(LocalDate date);

    /**
     * Findet alle Abwesenheiten für einen Schüler an einem bestimmten Datum
     */
    @Query("SELECT e FROM ExternalAbsence e WHERE e.studentLocalId = :studentLocalId AND e.date = :date")
    List<ExternalAbsence> findByStudentAndDate(
            @Param("studentLocalId") String studentLocalId,
            @Param("date") LocalDate date
    );

    /**
     * Prüft, ob ein Schüler zu einem bestimmten Zeitpunkt als abwesend gemeldet ist.
     * Berücksichtigt den genauen Zeitraum (from/to).
     */
    @Query("SELECT e FROM ExternalAbsence e WHERE e.studentLocalId = :studentLocalId " +
            "AND e.fromDateTime <= :dateTime AND e.toDateTime >= :dateTime")
    List<ExternalAbsence> findActiveAbsences(
            @Param("studentLocalId") String studentLocalId,
            @Param("dateTime") LocalDateTime dateTime
    );

    /**
     * Löscht alle Abwesenheiten für ein bestimmtes Datum (vor Neu-Import)
     */
    @Modifying
    @Query("DELETE FROM ExternalAbsence e WHERE e.date = :date")
    void deleteByDate(@Param("date") LocalDate date);

    /**
     * Löscht alte Abwesenheiten (älter als angegebenes Datum)
     */
    @Modifying
    @Query("DELETE FROM ExternalAbsence e WHERE e.date < :beforeDate")
    int deleteOlderThan(@Param("beforeDate") LocalDate beforeDate);
}
