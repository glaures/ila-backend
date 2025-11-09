package sandbox27.ila.backend.notifications;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import sandbox27.ila.backend.assignments.AssignmentsFinalEvent;
import sandbox27.ila.backend.notifications.log.NotificationLogService;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodService;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserManagementService;
import sandbox27.ila.backend.user.events.UserCreatedEvent;
import sandbox27.infrastructure.email.MailService;
import sandbox27.infrastructure.error.ErrorHandlingService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Log4j2
public class NotificationManager {

    private final UserManagementService userManagementService;
    private final MessageSource messageSource;
    @Value("${ila.url}")
    String ilaUrl;
    final MailService mailService;
    final ErrorHandlingService errorHandlingService;
    final NotificationLogService notificationLogService;
    final AssignmentFinalEmailModelGenerator assignmentFinalEmailModelGenerator;
    final PeriodService periodService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
    public void onUserCreatedEvent(UserCreatedEvent userCreatedEvent) {
        Map<String, Object> model = new HashMap<>();
        model.put("firstName", userCreatedEvent.firstName());
        model.put("login", userCreatedEvent.login());
        model.put("password", userCreatedEvent.password());
        model.put("ilaUrl", ilaUrl);
        try {
            mailService.sendHtml(userCreatedEvent.email(),
                    null,
                    "user-welcome",
                    model,
                    null);
        } catch (Throwable throwable) {
            errorHandlingService.handleError(throwable);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
    public void onAssignmentsFinalEvent(AssignmentsFinalEvent assignmentsFinalEvent){
        final String templateName = "assignments-final";
        final String topic = templateName + "_" + assignmentsFinalEvent.periodId();
        List<User> allStudents = userManagementService.getAllStudents();
        for(User user : allStudents){
            if (notificationLogService.hasBeenSent(user.getUserName(), topic)) {
                log.info("Benachrichtigung für {} mit Topic {} bereits gesendet - überspringe",
                        user.getUserName(), topic);
                continue;
            }
            try {
                Map<String, Object> model = assignmentFinalEmailModelGenerator.generateAssignmentFinalEmailModel(user.getUserName(), assignmentsFinalEvent.periodId());
                mailService.sendHtml(user.getEmail(),
                        null,
                        templateName,
                        model,
                        null);
                notificationLogService.logNotificationSent(user.getUserName(), topic);
            } catch (Throwable throwable) {
                errorHandlingService.handleError(throwable);
            }
        }
    }

}
