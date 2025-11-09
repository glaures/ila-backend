package sandbox27.ila.backend.notifications;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;
import sandbox27.infrastructure.email.MailService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Slf4j
class AssignmentFinalEmailTest {

    @Autowired
    private AssignmentFinalEmailModelGenerator modelGenerator;

    @Autowired
    private MailService mailService;

    @Autowired
    private PeriodRepository periodRepository;

    @Autowired
    private UserRepository userRepository;

    @Value("${ila.url}")
    String iLAUrl;


    @Test
    void sendAssignmentFinalEmailToVivianeKempe() throws Exception {
        // Arrange
        // String userName = "viviane.kempe";
        String userName = "elisabeth.hofmann";

        User user = userRepository.findById(userName)
                .orElseThrow(() -> new RuntimeException("User nicht gefunden: " + userName));

        Period currentPeriod = periodRepository.findByCurrent(true)
                .orElseThrow(() -> new RuntimeException("Keine aktuelle Period gefunden"));

        log.info("Versende Assignment-Email für User: {} ({}) in Period: {}",
                userName, user.getEmail(), currentPeriod.getName());

        // Act - Generiere Model
        Map<String, Object> model = modelGenerator.generateAssignmentFinalEmailModel(userName, currentPeriod.getId());

        // Assert - Überprüfe Model
        assertNotNull(model);
        assertTrue(model.containsKey("user"));
        assertTrue(model.containsKey("period"));
        assertTrue(model.containsKey("assignments"));
        assertTrue(model.containsKey("ilaUrl"));

        var assignments = (java.util.List<CourseAssignmentEmailDto>) model.get("assignments");
        log.info("Model generiert mit {} Zuweisungen", assignments.size());

        // Log der Zuweisungen
        assignments.forEach(assignment -> {
            log.info("  - {} {}-{}: {} (Präferenz: {})",
                    assignment.getDayOfWeekDisplay(),
                    assignment.startTime(),
                    assignment.endTime(),
                    assignment.courseName(),
                    assignment.preferenceIndex() != null ? assignment.preferenceIndex() : "keine");
        });

        // Act - Versende Email
        String subject = "Deine iLA-Kurszuweisungen für " + currentPeriod.getName();
        mailService.sendHtml(
                user.getEmail(),
                subject,
                "assignments-final",
                model,
                null  // kein Attachment
        );

        log.info("Email erfolgreich versendet an: {}", user.getEmail());
    }

    @Test
    void testModelGenerationForAllAssignedUsers() {
        // Test nur für Model-Generierung ohne Email-Versand
        Period currentPeriod = periodRepository.findByCurrent(true)
                .orElseThrow(() -> new RuntimeException("Keine aktuelle Period gefunden"));

        // Hole alle User mit Assignments in der aktuellen Period
        var usersWithAssignments = userRepository.findAll().stream()
                .filter(user -> {
                    try {
                        var model = modelGenerator.generateAssignmentFinalEmailModel(
                                user.getUserName(),
                                currentPeriod.getId()
                        );
                        var assignments = (java.util.List<?>) model.get("assignments");
                        return !assignments.isEmpty();
                    } catch (Exception e) {
                        return false;
                    }
                })
                .toList();

        log.info("Gefundene User mit Assignments: {}", usersWithAssignments.size());

        usersWithAssignments.forEach(user -> {
            var model = modelGenerator.generateAssignmentFinalEmailModel(
                    user.getUserName(),
                    currentPeriod.getId()
            );
            var assignments = (java.util.List<?>) model.get("assignments");
            log.info("  - {} ({}): {} Kurse",
                    user.getUserName(),
                    user.getEmail(),
                    assignments.size());
        });
    }

    @Test
    void testModelGenerationOnly() {
        // Arrange
        String userName = "viviane.kempe";

        Period currentPeriod = periodRepository.findByCurrent(true)
                .orElseThrow(() -> new RuntimeException("Keine aktuelle Period gefunden"));

        // Act
        Map<String, Object> model = modelGenerator.generateAssignmentFinalEmailModel(userName, currentPeriod.getId());

        // Assert
        assertNotNull(model);
        assertNotNull(model.get("user"));
        assertNotNull(model.get("period"));
        assertNotNull(model.get("assignments"));
        assertEquals(iLAUrl, model.get("ilaUrl"));

        // Log für manuelles Überprüfen
        var assignments = (java.util.List<CourseAssignmentEmailDto>) model.get("assignments");
        log.info("Anzahl Zuweisungen: {}", assignments.size());

        assignments.forEach(assignment -> {
            log.info("  - {} {}: {} (Präferenz: {})",
                    assignment.getDayOfWeekDisplay(),
                    assignment.startTime(),
                    assignment.courseName(),
                    assignment.preferenceIndex() != null ? assignment.preferenceIndex() : "keine");
        });
    }
}