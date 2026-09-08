package sandbox27.ila.backend.assignable;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sandbox27.ila.backend.assignments.CourseUserAssignment;
import sandbox27.ila.backend.assignments.CourseUserAssignmentRepository;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseBlockAssignment;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.exchange.CourseEligibilityService;
import sandbox27.ila.backend.exchange.EligibilityResult;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.user.Role;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;
import sandbox27.infrastructure.security.RequiredRole;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Welche Kurse einer Phase lassen sich einem Schüler noch zuweisen?
 * <p>
 * Grundlage der manuellen Zuweisung im Frontend: Die Verwaltung soll nicht raten müssen, welcher
 * Kurs für ein Kind überhaupt in Frage kommt. Die Regeln kommen aus
 * {@link CourseEligibilityService} – demselben Dienst, der die Wechselwünsche prüft; sie hier ein
 * zweites Mal auszuformulieren hieße, dass die Kopien beim nächsten Regelwechsel auseinanderlaufen.
 * <p>
 * Kurse, die für den Schüler von vornherein nicht in Frage kommen, erscheinen gar nicht:
 * falsche Klassenstufe, ausgeschlossenes Geschlecht, für ihn gesperrter Block, persönlicher
 * Kursausschluss, Platzhalter-Kurse und bereits zugewiesene Kurse. Was übrig bleibt, steht mit
 * {@code assignable} in der Liste – bei {@code false} mit dem Grund (Tageskonflikt, Kurszahl
 * erreicht), damit die Verwaltung sieht, was der Zuweisung im Weg steht. Volle Kurse bleiben
 * zuweisbar und tragen nur eine Warnung: Ob überbucht wird, entscheidet die Verwaltung.
 */
@RestController
@RequestMapping("/users/{userName}/assignable-courses")
@RequiredArgsConstructor
public class AssignableCoursesService {

    final UserRepository userRepository;
    final PeriodRepository periodRepository;
    final CourseRepository courseRepository;
    final CourseUserAssignmentRepository courseUserAssignmentRepository;
    final CourseBlockAssignmentRepository courseBlockAssignmentRepository;
    final CourseEligibilityService eligibilityService;

    @RequiredRole({Role.ADMIN_ROLE_NAME, Role.COURSE_INSTRUCTOR_ROLE_NAME})
    @GetMapping
    @Transactional(readOnly = true)
    public List<AssignableCourseDto> getAssignableCourses(@PathVariable("userName") String userName,
                                                          @RequestParam("period-id") Long periodId) throws ServiceException {
        User student = userRepository.findById(userName)
                .orElseThrow(() -> new ServiceException(ErrorCode.UserNotFound));
        if (!periodRepository.existsById(periodId))
            throw new ServiceException(ErrorCode.NotFound);

        List<CourseUserAssignment> currentAssignments =
                courseUserAssignmentRepository.findByUserAndCourse_Period_Id(student, periodId);
        Set<Long> assignedCourseIds = currentAssignments.stream()
                .map(a -> a.getCourse().getId())
                .collect(Collectors.toSet());

        Map<Long, Block> blocksByCourseId = new HashMap<>();
        for (CourseBlockAssignment cba : courseBlockAssignmentRepository.findAllByPeriodId(periodId)) {
            blocksByCourseId.putIfAbsent(cba.getCourse().getId(), cba.getBlock());
        }

        List<AssignableCourseDto> result = new ArrayList<>();
        for (Course course : courseRepository.findAllByPeriod_Id(periodId)) {
            if (assignedCourseIds.contains(course.getId())) continue;

            // Ein Kurs ohne Block ist noch nicht eingeplant und darf die Liste nicht sprengen –
            // die Regelprüfung würde über den fehlenden Block stolpern.
            Block block = blocksByCourseId.get(course.getId());
            if (block == null) continue;

            EligibilityResult eligibility = eligibilityService.checkManualAssignmentEligibility(
                    student, course, currentAssignments, periodId);
            if (eligibility.isExcludeFromList()) continue;

            int assignedSeats = courseUserAssignmentRepository.countByCourseAndBlock(course, block);
            result.add(new AssignableCourseDto(
                    course.getId(),
                    course.getCourseId(),
                    course.getName(),
                    block.getId(),
                    block.getName(),
                    block.getDayOfWeek() != null ? block.getDayOfWeek().name() : null,
                    block.getStartTime(),
                    block.getEndTime(),
                    Math.max(0, course.getMaxAttendees() - assignedSeats),
                    course.getMaxAttendees(),
                    assignedSeats,
                    eligibility.isEligible(),
                    eligibility.getReason(),
                    eligibility.getWarning(),
                    course.isManualAssignmentOnly()));
        }

        result.sort(Comparator
                .comparingInt((AssignableCourseDto dto) -> dayOrder(dto.dayOfWeek()))
                .thenComparing(AssignableCourseDto::startTime, Comparator.nullsLast(Comparator.<LocalTime>naturalOrder()))
                .thenComparing(AssignableCourseDto::name, Comparator.nullsLast(Comparator.<String>naturalOrder())));
        return result;
    }

    /** Wochentag als Zahl, Unbekanntes ans Ende – der Name kommt aus {@link DayOfWeek}. */
    private static int dayOrder(String dayOfWeek) {
        if (dayOfWeek == null) return Integer.MAX_VALUE;
        try {
            return DayOfWeek.valueOf(dayOfWeek).getValue();
        } catch (IllegalArgumentException e) {
            return Integer.MAX_VALUE;
        }
    }
}
