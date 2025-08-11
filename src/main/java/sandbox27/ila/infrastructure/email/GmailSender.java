package sandbox27.ila.infrastructure.email;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Set;

@Component
public class GmailSender {

    @Value("${spring.mail.from.display}")
    String mailFromDisplayName;
    @Value("${spring.mail.from}")
    String mailFrom;

    private static HttpTransport createHttpTransport() throws GeneralSecurityException,
            IOException {
        return GoogleNetHttpTransport.newTrustedTransport();
    }

    private static JsonFactory createJsonFactory() {
        JsonFactory jsonFactory = new JacksonFactory();
        return jsonFactory;
    }

    private static GoogleCredential createCredentials() throws IOException,
            GeneralSecurityException {
        Set<String> scopes = Collections.singleton(GmailScopes.MAIL_GOOGLE_COM);
        GoogleCredential credentialFromJson = GoogleCredential.fromStream(GmailSender.class.getResourceAsStream("/bw-backend-google-key.json"))
                .createScoped(scopes);
        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(createHttpTransport())
                .setJsonFactory(createJsonFactory())
                .setServiceAccountId(credentialFromJson.getServiceAccountId())
                .setServiceAccountPrivateKey(credentialFromJson.getServiceAccountPrivateKey())
                .setServiceAccountScopes(scopes)
                .setServiceAccountUser("guido.laures@4betterwork.com").build();
        return credential;
    }

    public void send(MimeMessage email) throws GeneralSecurityException, IOException, MessagingException {
        Gmail service = new Gmail.Builder(createHttpTransport(), createJsonFactory(), createCredentials())
                .setApplicationName("ILa")
                .build();
        email.setFrom(mailFromDisplayName);
        // Encode and wrap the MIME message into a gmail message
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        email.writeTo(buffer);
        byte[] rawMessageBytes = buffer.toByteArray();
        String encodedEmail = Base64.encodeBase64URLSafeString(rawMessageBytes);
        com.google.api.services.gmail.model.Message message = new com.google.api.services.gmail.model.Message();
        message.setRaw(encodedEmail);
        message.set("From", mailFromDisplayName);
        service.users().messages().send(mailFrom, message).execute();
    }
}
