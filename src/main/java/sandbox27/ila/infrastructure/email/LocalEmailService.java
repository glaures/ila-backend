package sandbox27.ila.infrastructure.email;

import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.activation.URLDataSource;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Profile({"development", "test", "dev"})
public class LocalEmailService implements EmailService {

    @Value("${spring.mail.from}")
    String mailFrom;
    final JavaMailSender javaMailSender;
    final Configuration freeMarkerConfiguration;

    public MimeMessage createMimeMessage() {
        return javaMailSender.createMimeMessage();
    }

    public void send(MessageProperties props) throws Exception {
        // configuration object is always in model
        Map<String, Object> origModel = props.getModel();
        Map<String, Object> newModel = new HashMap<>();
        newModel.putAll(origModel);
        props.setModel(newModel);
        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper messageHelper = new MimeMessageHelper(message, true);
        messageHelper.setFrom(mailFrom);
        messageHelper.setTo(props.getTo());
        if(props.isSentToSelf()){
            messageHelper.setBcc(mailFrom);
        }
        messageHelper.setSubject(props.getSubject());

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

        javaMailSender.send(message);
    }

    public void send(MimeMessage mimeMessage) throws Exception {
        mimeMessage.setFrom(mailFrom);
        javaMailSender.send(mimeMessage);
    }

}
