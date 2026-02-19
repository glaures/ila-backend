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
import sandbox27.ila.backend.exchange.events.ExchangeRequestFulfilledEvent;
import sandbox27.ila.backend.exchange.events.ExchangeRequestUnfulfillableEvent;
import sandbox27.ila.backend.notifications.log.NotificationLogService;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodService;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserManagementService;
import sandbox27.ila.backend.user.events.UserCreatedEvent;
import sandbox27.ila.backend.user.events.UserPasswordResetEvent;
import sandbox27.infrastructure.email.MailService;
import sandbox27.infrastructure.error.ErrorHandlingService;

import java.time.DayOfWeek;
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
    public void onUserPasswordReset(UserPasswordResetEvent userPasswordResetEvent) {
        Map<String, Object> model = new HashMap<>();
        model.put("firstName", userPasswordResetEvent.firstName());
        model.put("login", userPasswordResetEvent.login());
        model.put("password", userPasswordResetEvent.password());
        model.put("ilaUrl", ilaUrl);
        try {
            mailService.sendHtml(userPasswordResetEvent.email(),
                    null,
                    "user-password-reset",
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

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
    public void onExchangeRequestFulfilled(ExchangeRequestFulfilledEvent event) {
        Map<String, Object> model = new HashMap<>();
        model.put("firstName", event.studentFirstName());
        model.put("oldCourseName", event.oldCourseName());
        model.put("oldBlockName", event.oldBlockName());
        model.put("oldDayOfWeek", translateDayOfWeek(event.oldDayOfWeek()));
        model.put("newCourseName", event.newCourseName());
        model.put("newBlockName", event.newBlockName());
        model.put("newDayOfWeek", translateDayOfWeek(event.newDayOfWeek()));
        model.put("ilaUrl", ilaUrl);
        try {
            mailService.sendHtml(event.studentEmail(),
                    null,
                    "exchange-fulfilled",
                    model,
                    null);
            log.info("Wechsel-Erfolg-Email gesendet an {}", event.studentEmail());
        } catch (Throwable throwable) {
            errorHandlingService.handleError(throwable);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
    public void onExchangeRequestUnfulfillable(ExchangeRequestUnfulfillableEvent event) {
        Map<String, Object> model = new HashMap<>();
        model.put("firstName", event.studentFirstName());
        model.put("currentCourseName", event.currentCourseName());
        model.put("blockName", event.blockName());
        model.put("dayOfWeek", translateDayOfWeek(event.dayOfWeek()));
        model.put("desiredCourseNames", event.desiredCourseNames());
        model.put("reason", event.reason());
        model.put("ilaUrl", ilaUrl);
        try {
            mailService.sendHtml(event.studentEmail(),
                    null,
                    "exchange-unfulfillable",
                    model,
                    null);
            log.info("Wechsel-Misserfolg-Email gesendet an {}", event.studentEmail());
        } catch (Throwable throwable) {
            errorHandlingService.handleError(throwable);
        }
    }

    /**
     * Übersetzt englische Wochentag-Namen ins Deutsche
     */
    private String translateDayOfWeek(String dayOfWeek) {
        return switch (dayOfWeek) {
            case "MONDAY" -> "Montag";
            case "TUESDAY" -> "Dienstag";
            case "WEDNESDAY" -> "Mittwoch";
            case "THURSDAY" -> "Donnerstag";
            case "FRIDAY" -> "Freitag";
            case "SATURDAY" -> "Samstag";
            case "SUNDAY" -> "Sonntag";
            default -> dayOfWeek;
        };
    }

}