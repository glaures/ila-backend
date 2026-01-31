package sandbox27.ila.backend.period;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
public class Period {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    Long id;
    @Column(unique = true)
    String name;
    LocalDate startDate;
    LocalDate endDate;
    boolean current;
    @CreationTimestamp
    private LocalDateTime createdAt;
    /**
     * Start der Wechselphase (wann Schüler Wechselwünsche abgeben können)
     */
    private LocalDateTime exchangePhaseStart;
    /**
     * Ende der Wechselphase (Deadline für Wechselwünsche)
     */
    private LocalDateTime exchangePhaseEnd;

    /**
     * Prüft, ob die Wechselphase gerade aktiv ist
     */
    public boolean isExchangePhaseActive() {
        if (exchangePhaseStart == null || exchangePhaseEnd == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(exchangePhaseStart) && !now.isAfter(exchangePhaseEnd);
    }

    /**
     * Prüft, ob die Wechselphase konfiguriert ist
     */
    public boolean hasExchangePhase() {
        return exchangePhaseStart != null && exchangePhaseEnd != null;
    }

    public boolean isClosed() {
        return LocalDate.now().isAfter(endDate);
    }

}
