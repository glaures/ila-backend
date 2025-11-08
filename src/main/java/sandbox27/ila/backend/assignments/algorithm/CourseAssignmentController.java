package sandbox27.ila.backend.assignments.algorithm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.user.Role;
import sandbox27.infrastructure.security.RequiredRole;
import sandbox27.infrastructure.error.ServiceException;

import java.util.List;

@RestController
@RequestMapping("/periods/{periodId}/assign-courses")
@RequiredArgsConstructor
@Slf4j
public class CourseAssignmentController {

    private final CourseAssignmentService courseAssignmentService;

    @GetMapping("/history")
    @RequiredRole(Role.ADMIN_ROLE_NAME)
    public List<AssignmentResult> getAllAssignmentResults(@PathVariable Long periodId) {
        return courseAssignmentService.getAllAssignmentResultsForPeriod(periodId);
    }

    @PostMapping
    @RequiredRole(Role.ADMIN_ROLE_NAME)
    public ResponseEntity<AssignmentResult> assignCourses(@PathVariable Long periodId) {
        try {
            log.info("Starting course assignment for period {}", periodId);
            AssignmentResult result = courseAssignmentService.assignCourses(periodId);
            log.info("Course assignment completed successfully");
            return ResponseEntity.ok(result);
        } catch (ServiceException e) {
            log.error("Service error during course assignment: {}", e.toString());
            throw e; // Let the global exception handler deal with it
        } catch (Exception e) {
            log.error("Unexpected error during course assignment", e);
            throw e; // Let the global exception handler deal with it
        }
    }

    @DeleteMapping("/history/{assignmentResultId}")
    public void delteAssignmentResult(@PathVariable("assignmentResultId") Long assignmentResultId) {
        courseAssignmentService.deleteAssignmentResult(assignmentResultId);
    }
}