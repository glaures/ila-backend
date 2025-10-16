package sandbox27.ila.backend.problems;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sandbox27.ila.backend.assignements.CourseUserAssignmentRepository;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.user.Role;
import sandbox27.ila.backend.user.User;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;
import sandbox27.infrastructure.security.AuthenticatedUser;
import sandbox27.infrastructure.security.RequiredRole;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/problems")
@RequiredArgsConstructor
public class ProblemsService {

    final PeriodRepository periodRepository;
    final CourseUserAssignmentRepository courseUserAssignmentRepository;

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @GetMapping
    public List<ProblemDto> getAllProblems() throws ServiceException {
        Period period = periodRepository.findByCurrent(true).orElseThrow(() -> new ServiceException(ErrorCode.PeriodNotStartedYet));
        // alle Schüler mit weniger als 3 zugewiesenen Kursen
        return courseUserAssignmentRepository.findStudentsWithLessThanInPeriod(Role.STUDENT, period.getId(), 3)
                .stream()
                .map(s -> ProblemDto.builder()
                        .description("Schüler:in hat nur " + s.assignedCount() + " Kurse")
                        .type("notEnoughCourses")
                        .id(s.userName())
                        .build())
                .collect(Collectors.toList());
    }
}
