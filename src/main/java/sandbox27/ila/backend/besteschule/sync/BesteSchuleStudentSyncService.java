package sandbox27.ila.backend.besteschule.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sandbox27.ila.backend.besteschule.sync.BesteSchuleStudentDto.StudentResponse;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Synchronisiert die Beste.Schule Student-IDs mit den lokalen Benutzern.
 * Wird täglich beim Start und danach alle 24 Stunden ausgeführt.
 *
 * Ablauf:
 * 1. GET /students von Beste.Schule abrufen (alle Seiten)
 * 2. Für jeden Schüler: local_id mit User.internalId matchen
 * 3. Bei Match: User.besteSchuleId = student.id setzen
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
            log.info("Beste.Schule Student-ID Sync abgeschlossen: {} Schüler abgerufen, {} gematcht, {} aktualisiert, {} ohne Match",
                    result.totalFetched, result.matched, result.updated, result.unmatched);
        } catch (Exception e) {
            log.error("Fehler beim Beste.Schule Student-ID Sync: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public SyncResult doSync() {
        List<StudentResponse> students = studentClient.fetchAllStudents();

        if (students.isEmpty()) {
            log.warn("Keine Schüler von Beste.Schule abgerufen");
            return new SyncResult(0, 0, 0, 0);
        }

        // Alle lokalen User mit internalId laden und als Map aufbereiten
        List<User> allUsers = userRepository.findAll();
        Map<String, User> usersByInternalId = allUsers.stream()
                .filter(u -> u.getInternalId() != null && !u.getInternalId().isBlank())
                .collect(Collectors.toMap(User::getInternalId, Function.identity(), (a, b) -> a));

        int matched = 0;
        int updated = 0;
        int unmatched = 0;

        for (StudentResponse student : students) {
            if (student.localId() == null || student.localId().isBlank()) {
                continue;
            }

            User user = usersByInternalId.get(student.localId());

            if (user != null) {
                matched++;

                // Nur updaten wenn sich die ID geändert hat oder noch nicht gesetzt war
                if (user.getBesteSchuleId() == null || !user.getBesteSchuleId().equals(student.id())) {
                    user.setBesteSchuleId(student.id());
                    userRepository.save(user);
                    updated++;
                    log.debug("Beste.Schule ID {} zugewiesen an User {} ({})",
                            student.id(), user.getUserName(), user.getFirstName() + " " + user.getLastName());
                }
            } else {
                unmatched++;
                log.trace("Kein lokaler User für Beste.Schule Student {} {} (local_id={})",
                        student.forename(), student.name(), student.localId());
            }
        }

        return new SyncResult(students.size(), matched, updated, unmatched);
    }

    public record SyncResult(
            int totalFetched,
            int matched,
            int updated,
            int unmatched
    ) {}
}
