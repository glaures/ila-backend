package sandbox27.ila.infrastructure.email;

import lombok.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Builder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MessageProperties {

    String to;
    String subject;
    String template;
    boolean sentToSelf;
    Map<String, Object> model = new HashMap<>();
    Map<String, File> attachments = new HashMap<>();

}
