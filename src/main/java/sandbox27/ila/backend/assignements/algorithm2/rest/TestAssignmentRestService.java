package sandbox27.ila.backend.assignements.algorithm2.rest;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sandbox27.ila.backend.assignements.algorithm2.AssignmentServiceRunner;
import sandbox27.ila.backend.assignements.algorithm2.dtos.CourseUserAssignmentDTO;

import java.util.List;

@RestController
@RequestMapping("test-assignment")
@AllArgsConstructor
public class TestAssignmentRestService {

    final AssignmentServiceRunner assignmentServiceRunner;

    @GetMapping
    public List<CourseUserAssignmentDTO> testAssignment() throws Exception{
        return assignmentServiceRunner.runAssignments();
    }
}
