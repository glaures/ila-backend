package sandbox27.ila.backend.exclusion;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AddExclusionRequest {

    private String userName;

    private Integer grade;

    private Long blockId;

    private Long periodId;

    private String reason;

    public boolean isValid() {
        return blockId != null && periodId != null &&
                ((userName != null && !userName.isBlank()) || grade != null);
    }
}
