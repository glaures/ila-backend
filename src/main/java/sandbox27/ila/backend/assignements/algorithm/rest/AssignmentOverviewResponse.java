package sandbox27.ila.backend.assignements.algorithm.rest;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AssignmentOverviewResponse {

    private List<StudentAssignment> students;

}
