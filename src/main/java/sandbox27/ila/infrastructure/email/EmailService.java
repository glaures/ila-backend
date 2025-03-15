package sandbox27.ila.infrastructure.email;

import jakarta.mail.internet.MimeMessage;

public interface EmailService {

    MimeMessage createMimeMessage();

    void send(MessageProperties props) throws Exception;

    void send(MimeMessage mimeMessage) throws Exception;
}
