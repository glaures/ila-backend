package sandbox27.ila.backend.problems;

import lombok.*;
import org.springframework.web.bind.annotation.GetMapping;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProblemDto {

    String description;
    String type;
    String id;
}
