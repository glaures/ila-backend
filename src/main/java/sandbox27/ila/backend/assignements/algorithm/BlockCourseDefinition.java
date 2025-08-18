package sandbox27.ila.backend.assignements.algorithm;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BlockCourseDefinition {
    private String courseId;
    private Long blockId;
    private int min;
    private int max;
    private List<String> assignedStudents = new ArrayList<>();
}
