package sandbox27.ila.backend.assignments;

import lombok.*;

import java.util.Collections;
import java.util.List;

@Builder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Feedback {

    List<String> info = Collections.emptyList();
    List<String> warnings = Collections.emptyList();
    List<String> errors = Collections.emptyList();
}
