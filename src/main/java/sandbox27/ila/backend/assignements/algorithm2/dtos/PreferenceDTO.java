package sandbox27.ila.backend.assignements.algorithm2.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PreferenceDTO {
    private String userName;
    private Long blockId;
    private String courseId;
    private int preferenceIndex;
}
