package sandbox27.ila.backend.assignments;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.assignments.algorithm.AssignmentResult;
import sandbox27.ila.backend.assignments.algorithm.AssignmentResultRepository;
import sandbox27.ila.backend.assignments.algorithm.CourseAssignmentService;
import sandbox27.ila.backend.user.Role;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;
import sandbox27.infrastructure.security.RequiredRole;

import java.util.List;

@RestController
@RequestMapping("/assignment-results")
@RequiredArgsConstructor
public class AssignmentResultController {

    final AssignmentResultService assignmentResultService;

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @PutMapping("/mark-final/{assignmentResultId}")
    public List<AssignmentResult> markCourseAssignmentProcessFinal(@PathVariable long assignmentResultId) throws ServiceException {
        return assignmentResultService.markCourseAssignmentProcessFinal(assignmentResultId);
    }

    @DeleteMapping("/{assignmentResultId}")
    @RequiredRole(Role.ADMIN_ROLE_NAME)
    public void deleteAssignmentResult(@PathVariable("assignmentResultId") Long assignmentResultId) {
        assignmentResultService.deleteAssignmentResult(assignmentResultId);
    }

    @GetMapping("/is-finalized")
    public boolean isCurrentPeriodFinalized() {
        return assignmentResultService.isCurrentPeriodFinalized();
    }

}
