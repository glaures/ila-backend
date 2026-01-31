package sandbox27.ila.backend.attendance;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sandbox27.infrastructure.email.ReliableMailService;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service für die Benachrichtigung des Sekretariats bei unentschuldigten Abwesenheiten.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnexcusedAbsenceNotificationService {

    private final ReliableMailService mailService;

    @Value("${ila.notification.secretary-email:#{null}}")
    private String secretaryEmail;

    @Value("${ila.notification.enabled:true}")
    private boolean notificationsEnabled;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Sendet eine Benachrichtigung an das Sekretariat über eine unentschuldigte Abwesenheit.
     */
    public void notifyUnexcusedAbsence(UnexcusedAbsenceInfo absenceInfo) {
        if (!notificationsEnabled) {
            log.debug("Benachrichtigungen deaktiviert - E-Mail wird nicht gesendet");
            return;
        }

        if (secretaryEmail == null || secretaryEmail.isBlank()) {
            log.warn("Keine Sekretariats-E-Mail konfiguriert (ila.notification.secretary-email). " +
                            "Unentschuldigte Abwesenheit von {} wird nicht gemeldet.",
                    absenceInfo.getStudentFullName());
            return;
        }

        try {
            Map<String, Object> model = buildEmailModel(absenceInfo);

            String subject = String.format("Unentschuldigte Abwesenheit: %s (%d. Klasse) am %s",
                    absenceInfo.getStudentFullName(),
                    absenceInfo.studentGrade(),
                    absenceInfo.sessionDate().format(DATE_FORMAT));

            mailService.sendConfirmationAsync(
                    secretaryEmail,
                    subject,
                    "unexcused-absence",
                    model
            );

            log.info("Benachrichtigung über unentschuldigte Abwesenheit gesendet: {} in Kurs {} am {}",
                    absenceInfo.getStudentFullName(),
                    absenceInfo.courseName(),
                    absenceInfo.sessionDate());

        } catch (MessagingException e) {
            log.error("Fehler beim Senden der Benachrichtigung für {}: {}",
                    absenceInfo.getStudentFullName(), e.getMessage(), e);
        }
    }

    /**
     * Sendet Benachrichtigungen für mehrere unentschuldigte Abwesenheiten.
     */
    public void notifyUnexcusedAbsences(List<UnexcusedAbsenceInfo> absences) {
        for (UnexcusedAbsenceInfo absence : absences) {
            notifyUnexcusedAbsence(absence);
        }
    }

    private Map<String, Object> buildEmailModel(UnexcusedAbsenceInfo info) {
        Map<String, Object> model = new HashMap<>();

        // Schüler
        model.put("studentName", info.getStudentFullName());
        model.put("studentFirstName", info.studentFirstName());
        model.put("studentLastName", info.studentLastName());
        model.put("studentGrade", info.studentGrade());

        // Kurs
        model.put("courseName", info.courseName());
        model.put("courseInstructor", info.courseInstructor());

        // Termin
        model.put("sessionDate", info.sessionDate().format(DATE_FORMAT));
        model.put("dayOfWeek", info.dayOfWeek());
        model.put("courseStartTime", info.courseStartTime() != null
                ? info.courseStartTime().format(TIME_FORMAT) : "");
        model.put("courseEndTime", info.courseEndTime() != null
                ? info.courseEndTime().format(TIME_FORMAT) : "");

        return model;
    }
}