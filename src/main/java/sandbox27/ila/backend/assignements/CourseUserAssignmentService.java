package sandbox27.ila.backend.assignements;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.assignements.algorithm2.AssignmentAlgorithmService;
import sandbox27.ila.backend.assignements.algorithm2.AssignmentServiceRunner;
import sandbox27.ila.backend.assignements.algorithm2.dtos.CourseUserAssignmentDTO;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.course.CourseDto;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;
import sandbox27.ila.infrastructure.security.AuthenticatedUser;

import java.util.List;
import java.util.Optional;

@RestController
@Service
@Slf4j
@RequestMapping("/assignments")
@RequiredArgsConstructor
public class CourseUserAssignmentService {

    final CourseUserAssignmentRepository courseUserAssignmentRepository;
    final ModelMapper modelMapper;
    final AssignmentServiceRunner assignmentServiceRunner;
    private final BlockRepository blockRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    @GetMapping("/{blockId}")
    public CourseUserAssignmentResponse getCourseUserAssignment(@PathVariable("blockId") Long blockId,
                                                                @AuthenticatedUser User authenticatedUser) {
        Optional<CourseUserAssignment> assignmentOptional = courseUserAssignmentRepository.findByUserAndBlock_Id(authenticatedUser, blockId);
        return assignmentOptional.map(courseUserAssignment -> {
                    CourseDto courseDto = modelMapper.map(courseUserAssignment.getCourse(), CourseDto.class);
                    return new CourseUserAssignmentResponse(new CourseUserAssignmentDto(courseUserAssignment, courseDto));
                })
                .orElseGet(CourseUserAssignmentResponse::new);
    }

    @GetMapping
    public List<CourseUserAssignmentDto> getCourseUserAssignment(@RequestParam("course-id") Long courseId) {
        List<CourseUserAssignment> assignments = courseUserAssignmentRepository.findByCourse_id(courseId);
        return assignments
                .stream()
                .map(a -> {
                    return new CourseUserAssignmentDto(a, modelMapper.map(a.getCourse(), CourseDto.class));
                })
                .toList();
    }

    @PostMapping
    @Transactional
    public List<CourseUserAssignmentDTO> runAssignmentsProcess() throws Exception {
        List<CourseUserAssignmentDTO> assignmentDTOS = assignmentServiceRunner.runAssignments();
        // delete current assignments that are not predefined
        int deleted = courseUserAssignmentRepository.deleteAllByPreset(false);
        log.info("Deleted {} assignments", deleted);
        for (CourseUserAssignmentDTO assignmentDto : assignmentDTOS.stream().filter(a -> !a.isPredefined() && !a.getCourseName().equals(AssignmentAlgorithmService.PAUSE_COURSE_NAME)).toList()) {
            CourseUserAssignment assignment = CourseUserAssignment.builder()
                    .block(blockRepository.getReferenceById(assignmentDto.getBlockId()))
                    .course(courseRepository.getReferenceByCourseId(assignmentDto.getCourseId()))
                    .user(userRepository.getReferenceById(assignmentDto.getUserName()))
                    .build();
            courseUserAssignmentRepository.save(assignment);
        }
        return assignmentDTOS;
    }

}
