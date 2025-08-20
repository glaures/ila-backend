package sandbox27.ila.backend.preference;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.block.BlockService;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.course.CourseService;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.infrastructure.error.ErrorCode;
import sandbox27.ila.infrastructure.error.ServiceException;
import sandbox27.ila.infrastructure.security.AuthenticatedUser;

import java.time.LocalDate;
import java.util.List;

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
}
