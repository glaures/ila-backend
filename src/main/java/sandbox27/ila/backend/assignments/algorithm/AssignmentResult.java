package sandbox27.ila.backend.assignments.algorithm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentResult {

    private int totalStudents;
    private int assignedStudents;
    private int partiallyAssigned;
    private int unassigned;

    private double averagePriority;
    private Map<Integer, Long> priorityDistribution;

    private double averageFairnessScore;
    private double fairnessStdDeviation;

    public double getAssignmentRate() {
        return totalStudents > 0 ? (double) assignedStudents / totalStudents * 100 : 0;
    }
}