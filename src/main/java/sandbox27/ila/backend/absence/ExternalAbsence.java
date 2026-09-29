package sandbox27.ila.backend.absence;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Speichert Abwesenheitsmeldungen aus dem externen System "Beste.Schule".
 * Diese werden verwendet, um bei der Anwesenheitserfassung zu erkennen,
 * ob ein Schüler bereits als abwesend gemeldet ist.
 */
@Entity
@Table(name = "external_absence", indexes = {
        @Index(name = "idx_external_absence_date", columnList = "date"),
        @Index(name = "idx_external_absence_student", columnList = "student_local_id"),
        @Index(name = "idx_external_absence_bs_student", columnList = "beste_schule_student_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalAbsence {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    /**
     * ID der Abwesenheit im Beste.Schule System
     */
    @Column(name = "external_id", nullable = false)
    private Long externalId;

    /**
     * UUID des Schülers aus SaxSVS (entspricht User.internalId).
     * Beste.Schule liefert die local_id nicht mehr über die API aus; der Wert wird
     * deshalb beim Import aus dem lokal zugeordneten User übernommen und dient nur
     * noch der Nachvollziehbarkeit. Gematcht wird über {@link #besteSchuleStudentId}.
     */
    @Column(name = "student_local_id", nullable = false)
    private String studentLocalId;

    /**
     * Numerische Schüler-ID in Beste.Schule (student.id aus der API).
     * Schlüssel für die Zuordnung zum lokalen User (User.besteSchuleId) und
     * für das Eintragen von Abwesenheiten über die API.
     */
    @Column(name = "beste_schule_student_id")
    private Long besteSchuleStudentId;

    /**
     * Beginn der Abwesenheit
     */
    @Column(name = "from_date_time", nullable = false)
    private LocalDateTime fromDateTime;

    /**
     * Ende der Abwesenheit
     */
    @Column(name = "to_date_time", nullable = false)
    private LocalDateTime toDateTime;

    /**
     * Typ der Abwesenheit (z.B. "krank", "fehlend", "Freistellung")
     */
    @Column(name = "absence_type")
    private String absenceType;

    /**
     * Datum für schnelle Abfragen (abgeleitet aus fromDateTime)
     */
    @Column(name = "date", nullable = false)
    private LocalDate date;

    /**
     * Zeitpunkt des letzten Imports
     */
    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    /**
     * Prüft, ob die Abwesenheit einen bestimmten Zeitpunkt abdeckt
     */
    public boolean coversTime(LocalDateTime dateTime) {
        return !dateTime.isBefore(fromDateTime) && !dateTime.isAfter(toDateTime);
    }
}
