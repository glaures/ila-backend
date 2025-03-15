package sandbox27.ila.infrastructure.error;

import jakarta.mail.internet.MimeMessage;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import sandbox27.ila.infrastructure.email.EmailService;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ErrorHandlingService {

    Log log = LogFactory.getLog(ErrorHandlingService.class);
    @Value("${spring.mail.admin}")
    String mailTo;
    private final EmailService emailService;

    public ErrorHandlingService(EmailService emailService) {
        this.emailService = emailService;
    }

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

    private void notifyAdmin(String msg, Throwable... t){
        try {
            MimeMessage m = emailService.createMimeMessage();
            MimeMessageHelper message = new MimeMessageHelper(m, true);
            message.setTo(mailTo);
            StringBuilder buf = new StringBuilder();
            StringWriter sw = new StringWriter();
            if (t.length > 0) {
                message.setSubject("Fehler: " + msg + "\n" + t[0].getMessage());
                t[0].printStackTrace(new PrintWriter(sw));
            } else {
                message.setSubject("Meldung vom Server");
                sw.write(msg);
            }
            buf.append(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)).append("\n\n");
            buf.append(sw.getBuffer().toString());
            sw.close();
            message.setText(buf.toString(), true);
            emailService.send(m);
        } catch (Throwable t2) {
            if (t.length > 0)
                t[0].printStackTrace();
            t2.printStackTrace();
            return;
        }
    }

}
