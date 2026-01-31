package sandbox27.ila.backend.absence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sandbox27.ila.backend.absence.BesteSchuleDto.AbsenceResponse;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service für die Verwaltung externer Abwesenheiten aus Beste.Schule.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalAbsenceService {

    private final ExternalAbsenceRepository absenceRepository;
    private final BesteSchuleClient besteSchuleClient;
    private final UserRepository userRepository;

    private static final DateTimeFormatter BESTE_SCHULE_FORMAT = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Synchronisiert die Abwesenheiten für den heutigen Tag.
     * Wird vom Scheduler aufgerufen.
     */
    @Transactional
    public SyncResult syncAbsencesForToday() {
        return syncAbsencesForDate(LocalDate.now());
    }

    /**
     * Synchronisiert die Abwesenheiten für ein bestimmtes Datum.
     */
    @Transactional
    public SyncResult syncAbsencesForDate(LocalDate date) {
        log.info("Starte Sync der Abwesenheiten für {}", date);

        List<AbsenceResponse> allAbsences = besteSchuleClient.fetchAllAbsences();
        
        if (allAbsences.isEmpty()) {
            log.warn("Keine Abwesenheiten von Beste.Schule abgerufen");
            return new SyncResult(0, 0, 0, "Keine Daten von API");
        }

        // Filtere nur Abwesenheiten, die das angegebene Datum betreffen
        List<AbsenceResponse> relevantAbsences = allAbsences.stream()
                .filter(a -> coversDate(a, date))
                .toList();

        log.info("Von {} Abwesenheiten sind {} für {} relevant", 
                allAbsences.size(), relevantAbsences.size(), date);

        // Alte Einträge für dieses Datum löschen
        absenceRepository.deleteByDate(date);

        // Neue Einträge speichern
        int created = 0;
        int errors = 0;
        LocalDateTime now = LocalDateTime.now();

        for (AbsenceResponse absence : relevantAbsences) {
            try {
                ExternalAbsence entity = mapToEntity(absence, date, now);
                absenceRepository.save(entity);
                created++;
            } catch (Exception e) {
                log.warn("Fehler beim Importieren der Abwesenheit {}: {}", 
                        absence.id(), e.getMessage());
                errors++;
            }
        }

        log.info("Sync abgeschlossen: {} erstellt, {} Fehler", created, errors);
        return new SyncResult(relevantAbsences.size(), created, errors, null);
    }

    /**
     * Prüft, ob ein Schüler zu einem bestimmten Zeitpunkt als abwesend gemeldet ist.
     *
     * @param user     Der Benutzer (Schüler)
     * @param dateTime Der zu prüfende Zeitpunkt
     * @return Optional mit der Abwesenheit, falls vorhanden
     */
    public Optional<ExternalAbsence> getActiveAbsence(User user, LocalDateTime dateTime) {
        if (user.getInternalId() == null || user.getInternalId().isBlank()) {
            return Optional.empty();
        }

        List<ExternalAbsence> absences = absenceRepository.findActiveAbsences(
                user.getInternalId(), 
                dateTime
        );

        return absences.stream().findFirst();
    }

    /**
     * Prüft, ob ein Schüler zu einem bestimmten Zeitpunkt als abwesend gemeldet ist.
     */
    public boolean isStudentAbsent(User user, LocalDateTime dateTime) {
        return getActiveAbsence(user, dateTime).isPresent();
    }

    /**
     * Prüft, ob ein Schüler an einem Datum zu einer bestimmten Uhrzeit abwesend ist.
     */
    public boolean isStudentAbsent(User user, LocalDate date, LocalTime time) {
        return isStudentAbsent(user, LocalDateTime.of(date, time));
    }

    /**
     * Gibt alle Abwesenheiten für ein Datum zurück.
     */
    public List<ExternalAbsence> getAbsencesForDate(LocalDate date) {
        return absenceRepository.findByDate(date);
    }

    /**
     * Gibt die Abwesenheiten für mehrere Schüler an einem Datum/Uhrzeit zurück.
     * Nützlich für die Anwesenheitserfassung.
     *
     * @param userNames Liste der Benutzernamen
     * @param dateTime  Der zu prüfende Zeitpunkt
     * @return Map von userName zu Optional<ExternalAbsence>
     */
    public Map<String, Optional<ExternalAbsence>> getAbsencesForUsers(
            List<String> userNames, 
            LocalDateTime dateTime
    ) {
        // Lade alle Users mit ihren internalIds
        List<User> users = userRepository.findAllById(userNames);
        
        Map<String, Optional<ExternalAbsence>> result = new HashMap<>();
        
        for (User user : users) {
            result.put(user.getUserName(), getActiveAbsence(user, dateTime));
        }
        
        // Für nicht gefundene User: empty
        for (String userName : userNames) {
            result.putIfAbsent(userName, Optional.empty());
        }
        
        return result;
    }

    /**
     * Räumt alte Abwesenheiten auf (älter als angegebene Tage).
     */
    @Transactional
    public int cleanupOldAbsences(int daysToKeep) {
        LocalDate cutoffDate = LocalDate.now().minusDays(daysToKeep);
        int deleted = absenceRepository.deleteOlderThan(cutoffDate);
        log.info("Alte Abwesenheiten gelöscht: {} Einträge älter als {}", deleted, cutoffDate);
        return deleted;
    }

    /**
     * Prüft, ob eine Abwesenheit ein bestimmtes Datum abdeckt.
     */
    private boolean coversDate(AbsenceResponse absence, LocalDate date) {
        try {
            LocalDateTime from = parseDateTime(absence.from());
            LocalDateTime to = parseDateTime(absence.to());
            
            LocalDate fromDate = from.toLocalDate();
            LocalDate toDate = to.toLocalDate();
            
            return !date.isBefore(fromDate) && !date.isAfter(toDate);
        } catch (DateTimeParseException e) {
            log.warn("Ungültiges Datumsformat in Abwesenheit {}: from={}, to={}", 
                    absence.id(), absence.from(), absence.to());
            return false;
        }
    }

    /**
     * Mappt eine API-Response zu einer Entity.
     */
    private ExternalAbsence mapToEntity(AbsenceResponse response, LocalDate date, LocalDateTime fetchedAt) {
        LocalDateTime from = parseDateTime(response.from());
        LocalDateTime to = parseDateTime(response.to());
        
        String absenceType = response.type() != null ? response.type().name() : "unbekannt";
        String studentLocalId = response.student() != null ? response.student().localId() : null;
        
        if (studentLocalId == null) {
            throw new IllegalArgumentException("Keine student.local_id in Abwesenheit " + response.id());
        }

        return ExternalAbsence.builder()
                .externalId(response.id())
                .studentLocalId(studentLocalId)
                .fromDateTime(from)
                .toDateTime(to)
                .absenceType(absenceType)
                .date(date)
                .fetchedAt(fetchedAt)
                .build();
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        return LocalDateTime.parse(dateTimeStr, BESTE_SCHULE_FORMAT);
    }

    /**
     * Ergebnis einer Synchronisation
     */
    public record SyncResult(
            int totalRelevant,
            int created,
            int errors,
            String message
    ) {
        public boolean isSuccess() {
            return errors == 0 && message == null;
        }
    }
}
