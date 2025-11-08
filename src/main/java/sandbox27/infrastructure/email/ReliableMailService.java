package sandbox27.infrastructure.email;

import jakarta.mail.MessagingException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ReliableMailService {

    private final MailService mailService;

    public ReliableMailService(MailService mailService) {
        this.mailService = mailService;
    }

    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public void sendConfirmationAsync(String to, String subject, String tpl, Map<String, Object> model) throws MessagingException, MessagingException {
        mailService.sendHtml(to, subject, tpl, model, null);
    }


}
