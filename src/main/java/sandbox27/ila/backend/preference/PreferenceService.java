package sandbox27.ila.backend.preference;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.assignements.CourseUserAssignment;
import sandbox27.ila.backend.assignements.CourseUserAssignmentRepository;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.block.BlockService;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseCategory;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.course.CourseService;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.infrastructure.error.ErrorCode;
import sandbox27.ila.infrastructure.error.ServiceException;
import sandbox27.ila.infrastructure.security.AuthenticatedUser;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/preferences")
@RequiredArgsConstructor
public class PreferenceService {

    final BlockService blockService;
    final BlockRepository blockRepository;
    final CourseService courseService;
    final CourseRepository courseRepository;
    final PreferenceRepository preferenceRepository;
    final ModelMapper modelMapper;
    private final PeriodRepository periodRepository;
    private final CourseUserAssignmentRepository courseUserAssignmentRepository;

    @GetMapping("/{blockId}")
    public PreferencePayload getPreferences(@PathVariable("blockId") Long blockId,
                                            @AuthenticatedUser User authenticatedUser) {
        int grade = authenticatedUser.getGrade();
        PreferencePayload payload = new PreferencePayload();
        Block block = blockRepository.getReferenceById(blockId);
        payload.setBlockId(block.getId());
        payload.setCourses(courseService.findAllCoursesForBlock(block.getId(), grade));
        List<Preference> preferences = preferenceRepository.findByUserAndBlockOrderByPreferenceIndex(authenticatedUser, block);
        BlockPreferencesDto blockPreferencesDto = new BlockPreferencesDto(preferences);
        payload.setPreferences(blockPreferencesDto);
        payload.setPauseSelected(blockPreferencesDto.isPauseSelected());
        return payload;
    }

    @PostMapping("/{blockId}")
    @Transactional
    public PreferencePayload savePreferences(
            @PathVariable Long blockId,
            @RequestBody BlockPreferencesDto dto,
            @AuthenticatedUser User user) {
        Block block = blockRepository.findById(blockId).orElseThrow();
        if (block.getPeriod().getStartDate().isAfter(LocalDate.now())
                || block.getPeriod().getEndDate().isBefore(LocalDate.now())) {
            throw new ServiceException(ErrorCode.PeriodNotStartedYet);
        }
        preferenceRepository.deleteByUserAndBlock(user, block);
        for (int i = 0; i < dto.getPreferences().size(); i++) {
            Long courseId = dto.getPreferences().get(i);
            Course course = courseRepository.findById(courseId).orElseThrow();
            Preference pref = Preference.builder()
                    .user(user)
                    .block(block)
                    .course(course)
                    .preferenceIndex(dto.isPauseSelected() ? -1 : i)
                    .build();
            preferenceRepository.save(pref);
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
            CourseUserAssignment assignment = courseUserAssignmentRepository.findByUserAndBlock_Id(user, block.getId())
                    .orElse(null);
            CourseCategory assignmentCourseCategory = assignment == null ? null :
                    assignment.getCourse().getCourseCategories().stream().findFirst().orElse(CourseCategory.iLa);
            List<TopPreference> topPreferenceList = new ArrayList<>();
            if (assignment == null) {
                List<Preference> preferences = preferenceRepository.findByUserAndBlockOrderByPreferenceIndex(user, block);
                if(!preferences.isEmpty() && preferences.getFirst().getPreferenceIndex() != -1) {
                    for(int i = 0; i < Math.min(preferences.size(),3); i++) {
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
                            assignment.getCourse().getInstructor(),
                            assignmentCourseCategory) : null,
                    topPreferenceList
            ));
        }
        return result;
    }
}
