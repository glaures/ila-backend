package sandbox27.ila.backend.assignements.algorithm2.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CourseUserAssignmentDTO {
    private String userName;
    private String courseId;
    private String courseName;
    private Long blockId;
    private boolean predefined; // true = schon vorab festgelegt
    private boolean pause;
    private int preferenceIndex;
}
