package sandbox27.ila.backend.attendance;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sandbox27.ila.backend.absence.BesteSchuleClient;
import sandbox27.ila.backend.absence.BesteSchuleDto.CreateAbsenceRequest;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;
import sandbox27.infrastructure.email.ReliableMailService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service für die Meldung unentschuldigter Abwesenheiten an Beste.Schule.
 * Ersetzt die vorherige E-Mail-Benachrichtigung ans Sekretariat.
 *
 * Bei Fehlern wird eine E-Mail an den Admin gesendet.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnexcusedAbsenceNotificationService {

    private final BesteSchuleClient besteSchuleClient;
    private final UserRepository userRepository;
    private final ReliableMailService mailService;

    @Value("${ila.notification.admin-email:#{null}}")
    private String adminEmail;

    @Value("${ila.notification.enabled:true}")
    private boolean notificationsEnabled;

    /**
     * Feste Werte für den Beste.Schule API-Call (wie im Python-Skript definiert).
     */
    private static final int ABSENCE_TYPE_ID = 205;                  // "fehlend"
    private static final List<Integer> SUBJECT_IDS = List.of(39790); // "individuelles Lernangebot"

    private static final DateTimeFormatter BESTE_SCHULE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter BESTE_SCHULE_RECORDED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Meldet eine einzelne unentschuldigte Abwesenheit an Beste.Schule.
     *
     * @return Optional mit der von Beste.Schule vergebenen Absence-ID, falls
     *         der Eintrag erfolgreich war. Optional.empty() bei Fehler, fehlender
     *         Student-ID oder wenn Benachrichtigungen deaktiviert sind. Die ID
     *         sollte vom Aufrufer auf dem zugehörigen AttendanceEntry persistiert
     *         werden, um Doppelmeldungen zu verhindern und spätere Stornierung
     *         zu ermöglichen.
     */
    public Optional<Long> notifyUnexcusedAbsence(UnexcusedAbsenceInfo absenceInfo) {
        if (!notificationsEnabled) {
            log.debug("Benachrichtigungen deaktiviert - Abwesenheit wird nicht gemeldet");
            return Optional.empty();
        }

        // Beste.Schule ID direkt vom User holen
        Long studentId = resolveBesteSchuleStudentId(absenceInfo);
        if (studentId == null) {
            String errorMsg = String.format(
                    "Beste.Schule Student-ID ist nicht bekannt für %s (userName=%s). " +
                            "Abwesenheit am %s in Kurs %s kann nicht eingetragen werden. " +
                            "Wurde der Student-ID-Sync erfolgreich ausgeführt?",
                    absenceInfo.getStudentFullName(),
                    absenceInfo.studentUserName(),
                    absenceInfo.sessionDate(),
                    absenceInfo.courseName()
            );
            log.error(errorMsg);
            sendAdminNotification("Beste.Schule Student-ID fehlt", errorMsg);
            return Optional.empty();
        }

        CreateAbsenceRequest request = buildRequest(absenceInfo, studentId);

        Optional<Long> absenceId = besteSchuleClient.createAbsence(request);

        if (absenceId.isPresent()) {
            log.info("Abwesenheit in Beste.Schule eingetragen (Absence-ID {}): {} in Kurs {} am {}",
                    absenceId.get(),
                    absenceInfo.getStudentFullName(),
                    absenceInfo.courseName(),
                    absenceInfo.sessionDate());
        } else {
            String errorMsg = String.format(
                    "Fehler beim Eintragen der Abwesenheit in Beste.Schule: %s (Student-ID %d) in Kurs %s am %s",
                    absenceInfo.getStudentFullName(),
                    studentId,
                    absenceInfo.courseName(),
                    absenceInfo.sessionDate()
            );
            log.error(errorMsg);
            sendAdminNotification("Fehler bei Beste.Schule Abwesenheitsmeldung", errorMsg);
        }

        return absenceId;
    }

    /**
     * Storniert eine zuvor von uns angelegte Abwesenheit in Beste.Schule.
     *
     * Wird vom AttendanceService aufgerufen, wenn ein Schüler von "abwesend"
     * zurück auf "anwesend" gesetzt wird UND wir die Eintragung selbst
     * angelegt hatten (d.h. besteSchuleAbsenceId ist gesetzt).
     *
     * @return true bei Erfolg, false bei Fehler (Admin wird per Mail informiert).
     */
    public boolean cancelAbsence(long absenceId, String studentName, String courseName) {
        if (!notificationsEnabled) {
            log.debug("Benachrichtigungen deaktiviert - Storno wird nicht ausgeführt");
            return false;
        }

        boolean ok = besteSchuleClient.deleteAbsence(absenceId);
        if (ok) {
            log.info("Abwesenheit {} in Beste.Schule storniert ({} / Kurs {})",
                    absenceId, studentName, courseName);
        } else {
            String errorMsg = String.format(
                    "Storno der Abwesenheit %d in Beste.Schule fehlgeschlagen: %s in Kurs %s",
                    absenceId, studentName, courseName);
            log.error(errorMsg);
            sendAdminNotification("Fehler beim Storno einer Beste.Schule-Abwesenheit", errorMsg);
        }
        return ok;
    }

    /**
     * Ermittelt die Beste.Schule Student-ID vom User-Entity.
     */
    private Long resolveBesteSchuleStudentId(UnexcusedAbsenceInfo absenceInfo) {
        return userRepository.findById(absenceInfo.studentUserName())
                .map(User::getBesteSchuleId)
                .orElse(null);
    }

    /**
     * Baut den Request für die Beste.Schule API.
     * Der note_teacher-Text enthält Kursname und meldenden Kursleiter.
     */
    private CreateAbsenceRequest buildRequest(UnexcusedAbsenceInfo info, Long studentId) {
        LocalDate date = info.sessionDate();
        LocalTime startTime = info.courseStartTime();
        LocalTime endTime = info.courseEndTime();

        String from = LocalDateTime.of(date, startTime).format(BESTE_SCHULE_FORMAT);
        String to = LocalDateTime.of(date, endTime).format(BESTE_SCHULE_FORMAT);
        String recordedAt = LocalDateTime.now().format(BESTE_SCHULE_RECORDED_AT_FORMAT);

        String noteTeacher = String.format(
                "unentschuldigtes Fehlen im iLA Kurs %s gemeldet durch %s",
                info.courseName(),
                info.reportingTeacherFullName());

        return new CreateAbsenceRequest(
                studentId,
                ABSENCE_TYPE_ID,
                SUBJECT_IDS,
                from,
                to,
                noteTeacher,
                recordedAt
        );
    }

    /**
     * Sendet eine Fehler-E-Mail an den Admin.
     */
    private void sendAdminNotification(String subject, String errorMessage) {
        if (adminEmail == null || adminEmail.isBlank()) {
            log.warn("Keine Admin-E-Mail konfiguriert (ila.notification.admin-email). " +
                    "Fehlermeldung kann nicht zugestellt werden: {}", errorMessage);
            return;
        }

        try {
            Map<String, Object> model = new HashMap<>();
            model.put("subject", subject);
            model.put("errorMessage", errorMessage);
            model.put("timestamp", LocalDateTime.now().format(BESTE_SCHULE_RECORDED_AT_FORMAT));

            mailService.sendConfirmationAsync(
                    adminEmail,
                    "ILA: " + subject,
                    "admin-error-notification",
                    model
            );
        } catch (MessagingException e) {
            log.error("Fehler beim Senden der Admin-Benachrichtigung: {}", e.getMessage(), e);
        }
    }
}