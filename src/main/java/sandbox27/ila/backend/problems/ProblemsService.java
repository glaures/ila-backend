package sandbox27.ila.backend.problems;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sandbox27.ila.backend.assignments.CourseUserAssignmentRepository;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.user.Role;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;
import sandbox27.infrastructure.security.RequiredRole;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/problems")
@RequiredArgsConstructor
public class ProblemsService {

    final PeriodRepository periodRepository;
    final CourseUserAssignmentRepository courseUserAssignmentRepository;

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @GetMapping
    public List<ProblemDto> getAllProblems(@RequestParam("period-id") String periodId) throws ServiceException {
        Period period = periodRepository.findById(Long.parseLong(periodId))
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));

        List<ProblemDto> problems = new ArrayList<>();

        // Problem 1: Schüler mit weniger als 3 zugewiesenen Kursen
        // Nur prüfen, wenn die Einschreibung abgeschlossen ist (Startdatum erreicht)
        if (period.getEndDate() != null && period.getEndDate().isBefore(LocalDate.now())) {
            courseUserAssignmentRepository.findStudentsWithLessThanInPeriod(Role.STUDENT, period.getId(), 3)
                    .stream()
                    .map(s -> ProblemDto.builder()
                            .description("Schüler:in hat nur " + s.assignedCount() + " Kurse")
                            .type(ProblemTypes.notEnoughCourses)
                            .id(s.userName())
                            .build())
                    .forEach(problems::add);
        }

        // Problem 2: Schüler mit mehr als einem Kurs am gleichen Wochentag
        courseUserAssignmentRepository.findStudentsWithMultipleCoursesOnSameDayInPeriod(Role.STUDENT, period.getId())
                .stream()
                .map(s -> ProblemDto.builder()
                        .description(s.courseCount() + " Kurse am " + s.dayOfWeek().name())
                        .type(ProblemTypes.moreThan1AtTheSameDayOfWeek)
                        .id(s.userName())
                        .build())
                .forEach(problems::add);

        return problems;
    }
}