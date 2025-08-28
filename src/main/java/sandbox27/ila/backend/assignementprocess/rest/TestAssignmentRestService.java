package sandbox27.ila.backend.assignementprocess.rest;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sandbox27.ila.backend.assignementprocess.AssignmentServiceRunner;
import sandbox27.ila.backend.assignementprocess.dtos.CourseUserAssignmentDTO;
import sandbox27.ila.backend.period.PeriodRepository;

import java.util.List;

@RestController
@RequestMapping("test-assignment")
@AllArgsConstructor
public class TestAssignmentRestService {

    final AssignmentServiceRunner assignmentServiceRunner;
    final PeriodRepository periodRepository;

    @GetMapping
    public List<CourseUserAssignmentDTO> testAssignment() throws Exception {
        return assignmentServiceRunner.runAssignmentProcess(periodRepository.findByCurrent(true).get().getId());
    }
}
