package sandbox27.infrastructure.error;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import sandbox27.infrastructure.email.ReliableMailService;
import sandbox27.infrastructure.security.jwt.AuthenticatedUserResolver;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Log4j2
public class ErrorHandlingService {

    private final ErrorConfiguration errorConfiguration;
    private final ReliableMailService emailService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION, fallbackExecution = true)
    @Async
    public void handleError(ErrorEvent errorEvent) {
        handleError(errorEvent.t(), errorEvent.message());
    }

    public void handleWarning(String s) {
        log.warn(s);
        notifyAdmin("WARNING:" + s);
    }

    public void handleError(final Throwable t) {
        handleError(t, "");
    }

    public void handleError(final Throwable t, final String msg) {
        log.error(msg, t);
        notifyAdmin(msg, t);
    }

    private void notifyAdmin(String msg, Throwable... t) {
        try {
            StringBuilder buf = new StringBuilder();
            StringWriter sw = new StringWriter();
            String subject;
            if (t.length > 0) {
                subject = "Fehler: " + msg + "\n" + t[0].getMessage();
                t[0].printStackTrace(new PrintWriter(sw));
            } else {
                subject = "Meldung vom Server";
            }
            final Optional<String> webInfo = authenticatedUserResolver.resolveWebInformationIfPossible();
            if(webInfo.isPresent()) {
                msg = webInfo.get() + "\n" + msg;
            }
            sw.write(msg);
            buf.append(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)).append("\n\n");
            buf.append(sw.getBuffer().toString());
            sw.close();
            emailService.sendConfirmationAsync(errorConfiguration.getMailTo(),
                    subject,
                    "error",
                    Map.of("error", buf.toString()));
        } catch (Throwable t2) {
            if (t.length > 0)
                log.error(t[0]);
            log.error(t2);
        }
    }

}
