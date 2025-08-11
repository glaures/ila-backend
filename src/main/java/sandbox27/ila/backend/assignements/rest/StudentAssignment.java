package sandbox27.ila.backend.assignements.rest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentAssignment {
    private String studentName;
    private List<BlockAssignment> weeklyPlan;
}