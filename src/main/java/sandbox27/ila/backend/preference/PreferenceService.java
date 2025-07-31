package sandbox27.ila.backend.preference;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.block.BlockService;
import sandbox27.ila.backend.course.CourseService;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.frontend.marshalling.MarshallingConfiguration;
import sandbox27.ila.infrastructure.security.AuthenticatedUser;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/preferences")
@RequiredArgsConstructor
public class PreferenceService {

    final BlockService blockService;
    final BlockRepository blockRepository;
    final CourseService courseService;
    final ModelMapper modelMapper;

    @GetMapping("/{blockId}")
    public PreferencePayload getPreferences(@PathVariable("blockId") Long blockId,
                                            @AuthenticatedUser User authenticatedUser) {
        PreferencePayload payload = new PreferencePayload();
        Block block = blockRepository.getReferenceById(blockId);
        payload.setBlockId(block.getId());
        payload.setCourses(courseService.findAllCoursesForBlock(block.getId()));
        payload.setPreferences(List.of());
        return payload;
    }
}
