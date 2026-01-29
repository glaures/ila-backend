package sandbox27.ila.backend.preference;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.assignments.CourseUserAssignment;
import sandbox27.ila.backend.assignments.CourseUserAssignmentRepository;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.block.BlockService;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseCategory;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.course.CourseService;
import sandbox27.ila.backend.courseexclusions.CourseExclusionRepository;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.user.User;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ErrorHandlingService;
import sandbox27.infrastructure.error.ServiceException;
import sandbox27.infrastructure.security.AuthenticatedUser;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/preferences")
@RequiredArgsConstructor
public class PreferenceService {

    final BlockRepository blockRepository;
    final CourseRepository courseRepository;
    final PreferenceRepository preferenceRepository;
    private final PeriodRepository periodRepository;
    private final CourseUserAssignmentRepository courseUserAssignmentRepository;
    private final CourseExclusionRepository courseExclusionRepository;
    private final ErrorHandlingService errorHandlingService;

    @GetMapping("/{blockId}")
    public PreferencePayload getPreferences(@PathVariable("blockId") Long blockId,
                                            @AuthenticatedUser User authenticatedUser) {
        PreferencePayload payload = new PreferencePayload();
        payload.setBlockId(blockId);
        List<Long> preferencedCourseIds = preferenceRepository.findByUserAndBlock_IdOrderByPreferenceIndex(authenticatedUser, blockId)
                .stream().map(p -> p.getCourse().getId())
                .toList();
        payload.setPreferencedCourseIds(preferencedCourseIds);
        return payload;
    }

    @PostMapping("/{blockId}")
    @Transactional
    public PreferencePayload savePreferences(
            @PathVariable Long blockId,
            @RequestBody PreferencePayload preferencePayload,
            @AuthenticatedUser User user) {
        List<Long> preferencedCourseIds = preferencePayload.getPreferencedCourseIds();
        Block block = blockRepository.findById(blockId).orElseThrow();
        if (block.getPeriod().getStartDate().isAfter(LocalDate.now())
                || block.getPeriod().getEndDate().isBefore(LocalDate.now())) {
            throw new ServiceException(ErrorCode.PeriodNotStartedYet);
        }

        // Prüfen, ob der Benutzer von einem der gewählten Kurse ausgeschlossen ist
        if (!preferencedCourseIds.isEmpty()) {
            Set<Long> excludedCourseIds = courseExclusionRepository.findAllByCourseIdIn(
                            Set.copyOf(preferencedCourseIds)
                    ).stream()
                    .filter(e -> e.getUser().getUserName().equals(user.getUserName()))
                    .map(e -> e.getCourse().getId())
                    .collect(Collectors.toSet());

            if (!excludedCourseIds.isEmpty()) {
                // Ersten ausgeschlossenen Kurs für die Fehlermeldung finden
                Course excludedCourse = courseRepository.findById(excludedCourseIds.iterator().next()).orElse(null);
                String courseName = excludedCourse != null ? excludedCourse.getName() : "Unbekannt";
                throw new ServiceException(ErrorCode.UserExcludedFromCourse,
                        user.getFirstName() + " " + user.getLastName(), courseName);
            }
        }

        preferenceRepository.deleteByUserAndBlock(user, block);
        for (Long courseId : preferencedCourseIds) {
            Course course = courseRepository.findById(courseId).orElseThrow();
            Preference pref = Preference.builder()
                    .user(user)
                    .block(block)
                    .course(course)
                    .preferenceIndex(preferencedCourseIds.indexOf(courseId))
                    .build();
            pref = preferenceRepository.save(pref);
        }
        return getPreferences(blockId, user);
    }

    record AssignedCourse(
            long id,
            String name,
            String room,
            String instructor,
            CourseCategory category
    ) {
    }

    record TopPreference(
            long id,
            String name,
            CourseCategory category,
            int preferenceIndex
    ) {
    }

    public record BlockPreference(
            long blockId,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            long periodId,
            String status,
            AssignedCourse assignedCourse,
            List<TopPreference> topPreferences) {
    }

    @GetMapping("/overview")
    public List<BlockPreference> getOverview(@AuthenticatedUser User user) {
        Period currentPeriod = periodRepository.findByCurrent(true).get();
        List<BlockPreference> result = new ArrayList<>();
        List<Block> blocks = blockRepository.findAllByPeriod_idOrderByDayOfWeekAscStartTimeAsc(currentPeriod.getId());
        for (Block block : blocks) {
            // assignment suchen
            List<CourseUserAssignment> assignments = courseUserAssignmentRepository.findByUserAndBlock_Id(user, block.getId());
            CourseUserAssignment assignment = null;
            if(assignments.size() > 1) {
                errorHandlingService.handleWarning(user.getUserName() + " hat mehr als einen Block assigned. Nehme den ersten.");
            }
            if(!assignments.isEmpty()) assignment = assignments.getFirst();
            CourseCategory assignmentCourseCategory = assignment == null ? null :
                    assignment.getCourse().getCourseCategories().stream().findFirst().orElse(CourseCategory.iLa);
            List<TopPreference> topPreferenceList = new ArrayList<>();
            if (assignment == null) {
                List<Preference> preferences = preferenceRepository.findByUserAndBlock_IdOrderByPreferenceIndex(user, block.getId());
                if (!preferences.isEmpty() && preferences.getFirst().getPreferenceIndex() != -1) {
                    for (int i = 0; i < Math.min(preferences.size(), 3); i++) {
                        Preference preference = preferences.get(i);
                        CourseCategory preferenceCourseCategory = preference.getCourse().getCourseCategories().stream().findFirst().orElse(CourseCategory.iLa);
                        topPreferenceList.add(new TopPreference(
                                preference.getId(),
                                preference.getCourse().getName(),
                                preferenceCourseCategory,
                                preference.getPreferenceIndex()
                        ));
                    }
                }
            }
            if (assignment != null || !currentPeriod.isClosed()) {
                result.add(new BlockPreference(
                        block.getId(),
                        block.getDayOfWeek(),
                        block.getStartTime(),
                        block.getEndTime(),
                        currentPeriod.getId(),
                        assignment != null ? "ASSIGNED" : "OPEN",
                        assignment != null ? new AssignedCourse(assignment.getId(),
                                assignment.getCourse().getName(),
                                assignment.getCourse().getRoom(),
                                assignment.getCourse().getInstructor() != null ? assignment.getCourse().getInstructor().getLastName() : "?",
                                assignmentCourseCategory) : null,
                        topPreferenceList
                ));
            }
        }
        return result;
    }
}