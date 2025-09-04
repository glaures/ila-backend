package sandbox27.infrastructure.error;

import lombok.RequiredArgsConstructor;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sandbox27.infrastructure.email.ReliableMailService;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ErrorHandlingService {

    Log log = LogFactory.getLog(ErrorHandlingService.class);
    @Value("${spring.mail.admin}")
    String mailTo;
    private final ReliableMailService emailService;

    public void handleWarning(String s) {
        System.out.println("WARNING" + s);
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
            sw.write(msg);
            buf.append(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)).append("\n\n");
            buf.append(sw.getBuffer().toString());
            sw.close();
            emailService.sendConfirmationAsync(mailTo,
                    subject,
                    "error",
                    Map.of("error", buf.toString()));
        } catch (Throwable t2) {
            if (t.length > 0)
                t[0].printStackTrace();
            t2.printStackTrace();
            return;
        }
    }

}
