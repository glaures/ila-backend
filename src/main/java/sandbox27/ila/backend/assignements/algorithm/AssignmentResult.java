package sandbox27.ila.backend.assignements.algorithm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentResult {
    private Map<String, Map<Long, String>> assignments;
    private Map<String, Double> avgPreferenceLevels;
    private Map<Integer, Long> distribution;
}
