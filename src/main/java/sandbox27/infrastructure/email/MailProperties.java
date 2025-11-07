package sandbox27.infrastructure.email;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "spring.mail")
@Component
@Data
@ToString
public class MailProperties {

    private String host;
    private int port;
    private String username;
    @ToString.Exclude
    private String password;
    private String from;
    private String to;

}