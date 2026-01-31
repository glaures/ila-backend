package sandbox27.ila.backend.absence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler für die automatische Synchronisation der Abwesenheiten aus Beste.Schule.
 * 
 * Läuft alle 30 Minuten zwischen 10:00 und 14:00 Uhr (während der iLA-Kurszeiten).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExternalAbsenceSyncScheduler {

    private final ExternalAbsenceService externalAbsenceService;

    /**
     * Synchronisiert die Abwesenheiten für den heutigen Tag.
     * 
     * Cron-Ausdruck: "0 0/30 10-13 * * MON-FRI"
     * - Sekunde 0
     * - Alle 30 Minuten (0, 30)
     * - Stunden 10, 11, 12, 13 (also 10:00, 10:30, 11:00, 11:30, 12:00, 12:30, 13:00, 13:30)
     * - Jeden Tag im Monat
     * - Jeden Monat
     * - Montag bis Freitag
     */
    @Scheduled(cron = "0 0/30 10-13 * * MON-FRI")
    public void syncAbsences() {
        log.info("Starte geplanten Abwesenheits-Sync");
        
        try {
            ExternalAbsenceService.SyncResult result = externalAbsenceService.syncAbsencesForToday();
            
            if (result.isSuccess()) {
                log.info("Abwesenheits-Sync erfolgreich: {} Einträge synchronisiert", result.created());
            } else {
                log.warn("Abwesenheits-Sync mit Problemen: {} erstellt, {} Fehler, Nachricht: {}", 
                        result.created(), result.errors(), result.message());
            }
        } catch (Exception e) {
            log.error("Fehler beim Abwesenheits-Sync: {}", e.getMessage(), e);
        }
    }

    /**
     * Räumt alte Abwesenheiten auf (täglich um 2:00 Uhr nachts).
     * Behält nur die letzten 7 Tage.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldAbsences() {
        log.info("Starte Cleanup alter Abwesenheiten");
        
        try {
            int deleted = externalAbsenceService.cleanupOldAbsences(7);
            log.info("Cleanup abgeschlossen: {} alte Einträge gelöscht", deleted);
        } catch (Exception e) {
            log.error("Fehler beim Cleanup: {}", e.getMessage(), e);
        }
    }
}
