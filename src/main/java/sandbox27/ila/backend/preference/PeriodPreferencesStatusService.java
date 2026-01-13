package sandbox27.ila.backend.preference;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.user.Role;
import sandbox27.ila.backend.user.UserRepository;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;
import sandbox27.infrastructure.security.RequiredRole;

import java.util.List;

@RestController
@RequestMapping("/preference-status")
@RequiredArgsConstructor
public class PeriodPreferencesStatusService {

    public record PreferenceStatusResponse(
            Long periodId,
            String periodName,
            long totalStudents,
            long notStarted,
            long inProgress,
            long completed,
            List<CoursePopularity> popularCourses
            ) {
    }

    public record CoursePopularity(
            Long courseId,
            String courseIdentifier,
            String courseName,
            String instructor,
            long firstPreferenceCount,
            int maxAttendees,
            int utilizationPercentage
    ) {
    }

    final PeriodRepository periodRepository;
    final UserRepository userRepository;
    final PreferenceRepository preferenceRepository;
    final PeriodUserPreferencesSubmitStatusRepository periodUserPreferencesSubmitStatusRepository;

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @GetMapping
    public PreferenceStatusResponse getPreferenceStatus(@RequestParam("period-id") long periodId,
                                                        @RequestParam(value = "block-id", required = false) Long filterByBlockId) {
        Period period = periodRepository.findById(periodId).orElseThrow(() -> new ServiceException(ErrorCode.NotFound, periodId));
        long studentCount = userRepository.countByRole(Role.STUDENT);
        long studentsSubmitted = periodUserPreferencesSubmitStatusRepository.countByPeriodAndSubmitted(period, true);
        long studentsWithPreferences = preferenceRepository.countDistinctUsersByPeriod(period) - studentsSubmitted;
        List<Object[]> popularCoursesAsArray = (filterByBlockId == null
                ? preferenceRepository.findTopCoursesByFirstPreferenceAndPeriod(period, Pageable.ofSize(25))
                : preferenceRepository.findTopCoursesByFirstPreferenceAndPeriodAndBlock(period, filterByBlockId, Pageable.ofSize(25)));
        List<CoursePopularity> popularCourses = popularCoursesAsArray.stream().map(
                objArr -> {
                    Course course = (Course) objArr[0];
                    long firstPreferenceCount = (Long) objArr[1];
                    int utilization = (int) (((float) firstPreferenceCount / (float) course.getMaxAttendees()) * 100);
                    return new CoursePopularity(course.getId(), course.getCourseId(), course.getName(), course.getInstructor() != null ? course.getInstructor().getLastName() : "", firstPreferenceCount, course.getMaxAttendees(), utilization);
                }
        ).toList();
        return new PreferenceStatusResponse(periodId, period.getName(), studentCount, studentCount - studentsWithPreferences - studentsSubmitted, studentsWithPreferences, studentsSubmitted, popularCourses);
    }
}
