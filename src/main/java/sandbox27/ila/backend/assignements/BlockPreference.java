package sandbox27.ila.backend.assignements;

import lombok.*;

import java.util.List;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BlockPreference {
    private String studentId;
    private Long blockId;
    private List<String> preferences;
}
