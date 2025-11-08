package sandbox27.ila.backend.exclusion;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserBlockExclusionDTO {
    private Long id;
    private String userName;
    private String firstName;
    private String lastName;
    private int grade;
    private Long blockId;
    private Long periodId;
    private String reason;
    private LocalDateTime createdAt;
}

