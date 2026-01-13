package sandbox27.ila.backend.notifications;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sandbox27.ila.backend.assignments.CourseUserAssignment;
import sandbox27.ila.backend.assignments.CourseUserAssignmentRepository;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.preference.PreferenceRepository;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AssignmentFinalEmailModelGenerator {

    @Value("${ila.url}")
    String iLAUrl;
    final UserRepository userRepository;
    final PeriodRepository periodRepository;
    final PreferenceRepository  preferenceRepository;
    final CourseUserAssignmentRepository courseUserAssignmentRepository;

    @Transactional
    public Map<String, Object> generateAssignmentFinalEmailModel(String userName, Long periodId) {
        User user = userRepository.findById(userName)
                .orElseThrow(() -> new RuntimeException("User nicht gefunden"));

        Period period = periodRepository.findById(periodId)
                .orElseThrow(() -> new RuntimeException("Period nicht gefunden"));

        // Hole alle Assignments für den User in dieser Period
        List<CourseUserAssignment> assignments = courseUserAssignmentRepository
                .findByUserAndCourse_Period(user, period);

        // Erstelle DTOs und sortiere nach Tag und Zeit
        List<CourseAssignmentEmailDto> assignmentDtos = assignments.stream()
                .map(assignment -> {
                    Block block = assignment.getBlock();
                    Course course = assignment.getCourse();

                    // Suche ursprüngliche Präferenz (wenn vorhanden)
                    Integer preferenceIndex = preferenceRepository
                            .findByUserAndBlock_IdAndCourse_Id(user, block.getId(), course.getId())
                            .map(pref -> pref.getPreferenceIndex() + 1)  // 0-basiert -> 1-basiert
                            .orElse(null);

                    return new CourseAssignmentEmailDto(
                            course.getName(),
                            course.getDescription(),
                            course.getInstructor() != null ? course.getInstructor().getLastName() : "?",
                            course.getRoom(),
                            block.getDayOfWeek(),
                            block.getStartTime(),
                            block.getEndTime(),
                            preferenceIndex
                    );
                })
                .sorted(Comparator.comparingInt(CourseAssignmentEmailDto::getSortOrder))
                .toList();

        // Bereite Thymeleaf Context vor
        Map<String, Object> context = new HashMap<>();
        context.put("user", user);
        context.put("period", period);
        context.put("assignments", assignmentDtos);
        context.put("ilaUrl", iLAUrl);

        return context;
    }
}
