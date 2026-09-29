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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service für die Verwaltung externer Abwesenheiten aus Beste.Schule.
 *
 * Die Zuordnung einer Abwesenheit zu einem iLA-Benutzer läuft über die numerische
 * Beste.Schule-Schüler-ID (student.id aus der API ⇄ User.besteSchuleId). Die früher
 * genutzte SaxSVS-UUID (student.local_id) liefert die API nicht mehr aus.
 * User.besteSchuleId wird vom BesteSchuleStudentSyncService gepflegt.
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
            return new SyncResult(0, 0, 0, 0, "Keine Daten von API");
        }

        // Filtere nur Abwesenheiten, die das angegebene Datum betreffen
        List<AbsenceResponse> relevantAbsences = allAbsences.stream()
                .filter(a -> coversDate(a, date))
                .toList();

        log.info("Von {} Abwesenheiten sind {} für {} relevant", 
                allAbsences.size(), relevantAbsences.size(), date);

        Map<Long, User> usersByBesteSchuleId = loadUsersByBesteSchuleId();

        // Alte Einträge für dieses Datum löschen
        absenceRepository.deleteByDate(date);

        // Neue Einträge speichern
        int created = 0;
        int skipped = 0;
        int errors = 0;
        LocalDateTime now = LocalDateTime.now();

        for (AbsenceResponse absence : relevantAbsences) {
            try {
                Long studentId = absence.student() != null ? absence.student().id() : null;
                User user = studentId != null ? usersByBesteSchuleId.get(studentId) : null;

                if (user == null) {
                    // Schüler ist in iLA nicht (mehr) bekannt – für die Anwesenheitserfassung irrelevant
                    skipped++;
                    log.debug("Abwesenheit {} übersprungen: kein iLA-Benutzer zu Beste.Schule-Schüler {}",
                            absence.id(), studentId);
                    continue;
                }

                absenceRepository.save(mapToEntity(absence, user, date, now));
                created++;
            } catch (Exception e) {
                log.warn("Fehler beim Importieren der Abwesenheit {}: {}", 
                        absence.id(), e.getMessage());
                errors++;
            }
        }

        log.info("Sync abgeschlossen: {} erstellt, {} ohne iLA-Benutzer übersprungen, {} Fehler",
                created, skipped, errors);
        return new SyncResult(relevantAbsences.size(), created, skipped, errors, null);
    }

    /**
     * Lädt alle Benutzer, denen eine Beste.Schule-Schüler-ID zugeordnet ist, als Map.
     */
    private Map<Long, User> loadUsersByBesteSchuleId() {
        Map<Long, User> users = userRepository.findByBesteSchuleIdNotNull().stream()
                .collect(Collectors.toMap(User::getBesteSchuleId, Function.identity(), (a, b) -> a));

        if (users.isEmpty()) {
            log.warn("Kein Benutzer hat eine Beste.Schule-ID – bitte den Student-ID-Sync prüfen. " +
                    "Ohne diese Zuordnung können keine Abwesenheiten importiert werden.");
        }
        return users;
    }

    /**
     * Prüft, ob ein Schüler zu einem bestimmten Zeitpunkt als abwesend gemeldet ist.
     *
     * @param user     Der Benutzer (Schüler)
     * @param dateTime Der zu prüfende Zeitpunkt
     * @return Optional mit der Abwesenheit, falls vorhanden
     */
    public Optional<ExternalAbsence> getActiveAbsence(User user, LocalDateTime dateTime) {
        if (user.getBesteSchuleId() == null) {
            return Optional.empty();
        }

        List<ExternalAbsence> absences = absenceRepository.findActiveAbsences(
                user.getBesteSchuleId(),
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
        // Lade alle Users mit ihren Beste.Schule-IDs
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
     *
     * @param user der bereits über die Beste.Schule-Schüler-ID aufgelöste iLA-Benutzer
     */
    private ExternalAbsence mapToEntity(AbsenceResponse response, User user, LocalDate date, LocalDateTime fetchedAt) {
        LocalDateTime from = parseDateTime(response.from());
        LocalDateTime to = parseDateTime(response.to());
        
        String absenceType = response.type() != null ? response.type().name() : "unbekannt";

        return ExternalAbsence.builder()
                .externalId(response.id())
                .studentLocalId(user.getInternalId() != null ? user.getInternalId() : user.getUserName())
                .besteSchuleStudentId(user.getBesteSchuleId())
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
            int skipped,
            int errors,
            String message
    ) {
        public boolean isSuccess() {
            return errors == 0 && message == null;
        }
    }
}
