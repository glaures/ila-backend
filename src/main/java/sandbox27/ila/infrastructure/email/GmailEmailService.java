package sandbox27.ila.infrastructure.email;

import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Service
@RequiredArgsConstructor
@Profile("production")
public class GmailEmailService implements EmailService{

    @Value("${spring.mail.from}")
    String mailFrom;
    final Configuration freeMarkerConfiguration;
    final GmailSender gmailSender;
    final Environment environment;

    private String getTo(MessageProperties props) {
        if (Arrays.stream(environment.getActiveProfiles()).anyMatch(s -> s.equals("development")))
            return "guido.laures@gmail.com";
        return props.getTo();
    }

    public MimeMessage createMimeMessage() {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);
        return new MimeMessage(session);
    }

    public void send(MessageProperties props) throws Exception {
        Map<String, Object> origModel = props.getModel();
        Map<String, Object> newModel = new HashMap<>();
        newModel.putAll(origModel);
        props.setModel(newModel);
        MimeMessage message = createMimeMessage();
        MimeMessageHelper messageHelper = new MimeMessageHelper(message, true);
        messageHelper.setTo(getTo(props));
        messageHelper.setSubject(props.getSubject());
        if (props.isSentToSelf()) {
            messageHelper.setBcc(mailFrom);
        }

        Template template = freeMarkerConfiguration.getTemplate("/emails/" + props.getTemplate() + ".ftlh");
        StringWriter outWriter = new StringWriter();
        template.process(props.getModel(), outWriter);
        outWriter.close();
        String htmlText = outWriter.getBuffer().toString();
        String plainText = new HtmlToPlainText().getPlainText(Jsoup.parse(htmlText));
        messageHelper.setText(plainText, htmlText);
        for (String fileName : props.getAttachments().keySet()) {
            messageHelper.addAttachment(fileName, props.getAttachments().get(fileName));
        }

        /*
        String logoImgUrl = cloudinaryService.getImageUrl("logo_png", 120);
        messageHelper.addInline("logo", new URLDataSource(new URL(logoImgUrl)));
        */
        gmailSender.send(message);
    }

    public void send(MimeMessage mimeMessage) throws Exception {
        gmailSender.send(mimeMessage);
    }

}
