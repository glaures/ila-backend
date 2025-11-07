package sandbox27.infrastructure.error;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "sandbox27.infrastructure.error")
@Component
@Data
@ToString
public class ErrorConfiguration {

    String mailTo;
}
