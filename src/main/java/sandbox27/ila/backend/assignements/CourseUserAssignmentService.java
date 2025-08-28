package sandbox27.ila.backend.assignements;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.assignementprocess.AssignmentAlgorithmService;
import sandbox27.ila.backend.assignementprocess.dtos.CourseUserAssignmentDTO;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.course.CourseDto;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.user.Role;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;
import sandbox27.ila.infrastructure.error.ErrorCode;
import sandbox27.ila.infrastructure.error.ServiceException;
import sandbox27.ila.infrastructure.security.AuthenticatedUser;

import java.util.Collections;
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
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    final CourseBlockAssignmentRepository courseBlockAssignmentRepository;

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
    public List<CourseUserAssignmentDto> getCourseUserAssignment(@RequestParam(value = "course-id", required = false) Long courseId,
                                                                 @RequestParam(value = "user-name", required = false) String userName) {
        List<CourseUserAssignment> assignments = Collections.emptyList();
        if (courseId != null) {
            assignments = courseUserAssignmentRepository.findByCourse_id(courseId);
        } else if (userName != null) {
            assignments = courseUserAssignmentRepository.findByUser_userName(userName);
        }
        return assignments
                .stream()
                .map(a -> {
                    return new CourseUserAssignmentDto(a, modelMapper.map(a.getCourse(), CourseDto.class));
                })
                .toList();
    }

    public record CourseUserAssignmentPayload(
            String userName,
            String courseId
    ) {
    }

    @PostMapping
    public Feedback assignCourseToUser(@RequestBody CourseUserAssignmentPayload courseUserAssignmentPayload,
                                       @AuthenticatedUser User authenticatedUser) throws ServiceException {
        if (!authenticatedUser.getRoles().contains(Role.ADMIN))
            throw new ServiceException(ErrorCode.AccessDenied);
        User user = userRepository.findById(courseUserAssignmentPayload.userName)
                .orElseThrow(() -> new ServiceException(ErrorCode.UserNotFound));
        Course course = courseRepository.findByCourseId(courseUserAssignmentPayload.courseId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));
        Block block = courseBlockAssignmentRepository.findAllByCourse(course).getFirst().getBlock();
        ;
        CourseUserAssignment courseUserAssignment = CourseUserAssignment.builder()
                .user(user)
                .course(course)
                .block(block)
                .build();
        courseUserAssignmentRepository.save(courseUserAssignment);
        return Feedback.builder()
                .infos(List.of("Zuweisung gespeichert."))
                .build();
    }

    @Transactional
    @DeleteMapping("/{assignmentId}")
    public Feedback deleteAssignment(@PathVariable("assignmentId") Long assignmentId,
                                     @AuthenticatedUser User authenticatedUser) throws ServiceException {
        if (!authenticatedUser.getRoles().contains(Role.ADMIN))
            throw new ServiceException(ErrorCode.AccessDenied);
        CourseUserAssignment assignment = courseUserAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));
        courseUserAssignmentRepository.delete(assignment);
        return Feedback.builder()
                .infos(List.of("Die Kurszuordnung wurde entfernt"))
                .build();
    }


}
