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
        @Index(name = "idx_external_absence_student", columnList = "student_local_id")
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
     * UUID des Schülers aus SaxSVS (entspricht User.internalId)
     */
    @Column(name = "student_local_id", nullable = false)
    private String studentLocalId;

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
