package sandbox27.ila.backend.notifications;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import sandbox27.ila.backend.user.events.UserCreatedEvent;
import sandbox27.infrastructure.email.MailService;
import sandbox27.infrastructure.error.ErrorHandlingService;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationManager {

    @Value("${ila.url}")
    String ilaUrl;
    final MailService mailService;
    final ErrorHandlingService errorHandlingService;

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

}
