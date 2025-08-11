package sandbox27.ila.infrastructure.email.gmail;

import com.google.api.client.googleapis.GoogleUtils;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.apache.commons.codec.binary.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;

public class SendEmailWithGmailTest {

    private static HttpTransport _createHttpTransport() throws GeneralSecurityException,
            IOException {
        HttpTransport httpTransport = new NetHttpTransport.Builder()
                .trustCertificates(GoogleUtils.getCertificateTrustStore())
                .build();
        return httpTransport;
    }

    private static JsonFactory _createJsonFactory() {
        JsonFactory jsonFactory = new JacksonFactory();
        return jsonFactory;
    }

    private static GoogleCredential _createCredential() throws IOException,
            GeneralSecurityException {
        Set<String> scopes = Collections.singleton(GmailScopes.MAIL_GOOGLE_COM);
        GoogleCredential credentialFromJson = GoogleCredential.fromStream(SendEmailWithGmailTest.class.getResourceAsStream("/bw-backend-google-key.json"))
                .createScoped(scopes);
        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(_createHttpTransport())
                .setJsonFactory(_createJsonFactory())
                .setServiceAccountId(credentialFromJson.getServiceAccountId())
                .setServiceAccountPrivateKey(credentialFromJson.getServiceAccountPrivateKey())
                .setServiceAccountScopes(scopes)
                .setServiceAccountUser("guido.laures@4betterwork.com").build();
        return credential;
    }

    public static void main(String... args) throws IOException, GeneralSecurityException, MessagingException {
        // Build a new authorized API client service.
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Gmail service = new Gmail.Builder(HTTP_TRANSPORT, _createJsonFactory(), _createCredential())
                .setApplicationName("ILa")
                .build();

        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);
        MimeMessage email = new MimeMessage(session);

        // email.setFrom(new InternetAddress("Guido Laures <info@4betterwork.com>"));
        email.addRecipient(Message.RecipientType.TO,
                new InternetAddress("guido.laures@gmail.com"));
        email.setFrom("4BetterWork <guido.laures@4betterwork.com>");
        email.setSubject("Test");
        email.setText("Body");

        // Encode and wrap the MIME message into a gmail message
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        email.writeTo(buffer);
        byte[] rawMessageBytes = buffer.toByteArray();
        String encodedEmail = Base64.encodeBase64URLSafeString(rawMessageBytes);
        com.google.api.services.gmail.model.Message message = new com.google.api.services.gmail.model.Message();
        message.setRaw(encodedEmail);

        message.set("From", "4BetterWork <guido.laures@4betterwork.com>");
        service.users().messages().send("guido.laures@4betterwork.com", message).execute();

    }
}
