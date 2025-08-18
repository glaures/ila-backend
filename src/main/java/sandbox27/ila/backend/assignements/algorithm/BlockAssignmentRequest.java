package sandbox27.ila.backend.assignements.algorithm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlockAssignmentRequest {
    private List<BlockPreference> blockPreferences;
    private List<BlockCourseDefinition> blockCourses;
}
