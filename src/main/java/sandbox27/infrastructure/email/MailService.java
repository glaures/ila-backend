package sandbox27.infrastructure.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.lang.Nullable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

@Service
public class MailService {
    private final JavaMailSender mailSender;
    private final TemplateEngine thymeleaf; // falls Templates genutzt werden

    public MailService(JavaMailSender mailSender, TemplateEngine thymeleaf) {
        this.mailSender = mailSender;
        this.thymeleaf = thymeleaf;
    }

    public void sendSimple(String to, String subject, String text) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(text);
        mailSender.send(msg);
    }

    public void sendHtml(String to, String subject, String templateName, Map<String, Object> model,
                         @Nullable File attachment) throws MessagingException {
        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
        helper.setTo(to);
        helper.setSubject(subject);

        Context ctx = new Context(Locale.GERMAN);
        model.forEach(ctx::setVariable);
        String html = thymeleaf.process(templateName, ctx);
        helper.setText(html, true);

        if (attachment != null) {
            helper.addAttachment(attachment.getName(), attachment);
        }

        mailSender.send(mime);
    }
}
