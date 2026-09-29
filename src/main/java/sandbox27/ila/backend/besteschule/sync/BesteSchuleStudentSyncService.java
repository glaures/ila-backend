package sandbox27.ila.backend.besteschule.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sandbox27.ila.backend.besteschule.sync.BesteSchuleStudentDto.StudentResponse;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Synchronisiert die Beste.Schule Student-IDs mit den lokalen Benutzern.
 * Wird täglich beim Start und danach alle 24 Stunden ausgeführt.
 *
 * Ablauf:
 * 1. GET /students von Beste.Schule abrufen (alle Seiten)
 * 2. Für jeden Schüler: über Vor- und Nachname einen lokalen User suchen
 * 3. Bei eindeutigem Match: User.besteSchuleId = student.id setzen
 *
 * Gematcht wird über den Namen, weil Beste.Schule die SaxSVS-UUID (local_id)
 * nicht mehr über die API ausliefert. Beste.Schule führt alle Vornamen
 * ("Hadjar Rasulovna"), IServ nur den Rufnamen ("Hadjar") – deshalb wird auf
 * der Beste.Schule-Seite nur der erste Vorname verglichen. Mehrdeutige Namen
 * werden übersprungen, um keine falsche ID zuzuordnen.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BesteSchuleStudentSyncService {

    private final BesteSchuleStudentClient studentClient;
    private final UserRepository userRepository;

    /**
     * Täglicher Sync: beim Start sofort, dann alle 24h.
     */
    @Scheduled(initialDelay = 0, fixedDelay = 24 * 60 * 60 * 1000)
    public void syncStudentIds() {
        log.info("Starte Beste.Schule Student-ID Sync...");

        try {
            SyncResult result = doSync();
            log.info("Beste.Schule Student-ID Sync abgeschlossen: {} Schüler abgerufen, {} gematcht, " +
                            "{} aktualisiert, {} ohne Match, {} mehrdeutig",
                    result.totalFetched, result.matched, result.updated, result.unmatched, result.ambiguous);
        } catch (Exception e) {
            log.error("Fehler beim Beste.Schule Student-ID Sync: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public SyncResult doSync() {
        List<StudentResponse> students = studentClient.fetchAllStudents();

        if (students.isEmpty()) {
            log.warn("Keine Schüler von Beste.Schule abgerufen");
            return new SyncResult(0, 0, 0, 0, 0);
        }

        Map<String, List<User>> usersByName = indexUsersByName(userRepository.findAll());

        int matched = 0;
        int updated = 0;
        int unmatched = 0;
        int ambiguous = 0;

        for (StudentResponse student : students) {
            String key = nameKey(firstForename(student.forename()), student.name());
            if (key == null) {
                continue;
            }

            List<User> candidates = usersByName.getOrDefault(key, List.of());

            if (candidates.isEmpty()) {
                unmatched++;
                log.trace("Kein lokaler User für Beste.Schule Student {} {} (id={})",
                        student.forename(), student.name(), student.id());
                continue;
            }

            if (candidates.size() > 1) {
                ambiguous++;
                log.warn("Mehrdeutiger Name für Beste.Schule Student {} {} (id={}): {} lokale User – übersprungen",
                        student.forename(), student.name(), student.id(), candidates.size());
                continue;
            }

            User user = candidates.get(0);
            matched++;

            // Nur updaten wenn sich die ID geändert hat oder noch nicht gesetzt war
            if (user.getBesteSchuleId() == null || !user.getBesteSchuleId().equals(student.id())) {
                user.setBesteSchuleId(student.id());
                userRepository.save(user);
                updated++;
                log.debug("Beste.Schule ID {} zugewiesen an User {} ({})",
                        student.id(), user.getUserName(), user.getFirstName() + " " + user.getLastName());
            }
        }

        return new SyncResult(students.size(), matched, updated, unmatched, ambiguous);
    }

    /**
     * Indiziert die lokalen User über ihren normalisierten Namen. Mehrere User mit
     * identischem Namen landen bewusst in derselben Liste – sie werden beim Sync
     * als mehrdeutig übersprungen.
     */
    private Map<String, List<User>> indexUsersByName(List<User> users) {
        Map<String, List<User>> index = new HashMap<>();
        for (User user : users) {
            String key = nameKey(firstForename(user.getFirstName()), user.getLastName());
            if (key != null) {
                index.computeIfAbsent(key, k -> new ArrayList<>()).add(user);
            }
        }
        return index;
    }

    /**
     * Erster Vorname einer ggf. mehrteiligen Vornamensfolge ("Hadjar Rasulovna" → "Hadjar").
     */
    private static String firstForename(String forename) {
        if (forename == null) {
            return null;
        }
        String trimmed = forename.trim();
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }

    /**
     * Vergleichsschlüssel aus Vor- und Nachname: kleingeschrieben, ohne Diakritika,
     * damit "Aßmann"/"Assmann" und "Böhm"/"Boehm"-Varianten aus beiden Systemen zusammenfinden.
     */
    private static String nameKey(String forename, String lastName) {
        String first = normalize(forename);
        String last = normalize(lastName);
        if (first.isEmpty() || last.isEmpty()) {
            return null;
        }
        return first + "|" + last;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lower = value.trim().toLowerCase(Locale.GERMAN).replace("ß", "ss");
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").replaceAll("\\s+", " ");
    }

    public record SyncResult(
            int totalFetched,
            int matched,
            int updated,
            int unmatched,
            int ambiguous
    ) {}
}
