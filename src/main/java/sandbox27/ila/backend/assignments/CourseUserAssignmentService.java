package sandbox27.ila.backend.assignments;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.block.BlockDto;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.course.CourseDto;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.user.Role;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;
import sandbox27.infrastructure.security.AuthenticatedUser;
import sandbox27.infrastructure.security.RequiredRole;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
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
    @Transactional
    public CourseUserAssignmentResponse getCourseUserAssignment(@PathVariable("blockId") Long blockId,
                                                                @AuthenticatedUser User authenticatedUser) {
        Optional<CourseUserAssignment> assignmentOptional = courseUserAssignmentRepository.findByUserAndBlock_Id(authenticatedUser, blockId);
        return assignmentOptional.map(courseUserAssignment -> {
                    CourseDto courseDto = modelMapper.map(courseUserAssignment.getCourse(), CourseDto.class);
                    BlockDto blockDto = modelMapper.map(courseUserAssignment.getBlock(), BlockDto.class);
                    return new CourseUserAssignmentResponse(new CourseUserAssignmentDto(courseUserAssignment, courseDto, blockDto));
                })
                .orElseGet(CourseUserAssignmentResponse::new);
    }

    @GetMapping
    @Transactional
    public List<CourseUserAssignmentDto> getCourseUserAssignment(@RequestParam(value = "course-id", required = false) Long courseId,
                                                                 @RequestParam(value = "user-name", required = false) String userName,
                                                                 @RequestParam(value = "period-id", required = false) Long periodId) {
        List<CourseUserAssignment> assignments = Collections.emptyList();
        if (courseId != null) {
            assignments = courseUserAssignmentRepository.findByCourse_idOrderByUser_LastName(courseId);
        } else if (userName != null) {
            if(periodId == null)
                throw new ServiceException(ErrorCode.FieldRequired, "Phase");
            assignments = courseUserAssignmentRepository.findByUser_userNameAndCourse_Period_Id(userName, periodId);
        }
        return assignments
                .stream()
                .map(a -> {
                    return new CourseUserAssignmentDto(a, modelMapper.map(a.getCourse(), CourseDto.class), modelMapper.map(a.getBlock(), BlockDto.class));
                })
                .toList();
    }

    public record CourseUserAssignmentPayload(
            String userName,
            String courseId
    ) {
    }

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @Transactional
    @PostMapping
    public Feedback assignCourseToUser(@RequestBody CourseUserAssignmentPayload courseUserAssignmentPayload) throws ServiceException {
        User user = userRepository.findById(courseUserAssignmentPayload.userName)
                .orElseThrow(() -> new ServiceException(ErrorCode.UserNotFound));
        Course course = courseRepository.findByCourseId(courseUserAssignmentPayload.courseId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));
        Block block = courseBlockAssignmentRepository.findAllByCourse(course).getFirst().getBlock();
        CourseUserAssignment courseUserAssignment = CourseUserAssignment.builder()
                .user(user)
                .course(course)
                .block(block)
                .build();
        courseUserAssignmentRepository.save(courseUserAssignment);
        return Feedback.builder()
                .info(List.of("Zuweisung gespeichert."))
                .build();
    }


    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @Transactional
    @PostMapping("/copy-assignments")
    public Feedback copyAssignments(@RequestParam("source-course-id") Long sourceCourseId,
                                     @RequestParam("destination-course-id") Long destinationCourseId) throws ServiceException {
        Course sourceCourse = courseRepository.getReferenceById(sourceCourseId);
        Course destinationCourse = courseRepository.getReferenceById(destinationCourseId);
        List<CourseUserAssignment> allAssignments = courseUserAssignmentRepository.findByCourse_idOrderByUser_LastName(sourceCourseId);
        Set<User> alreadyAssignedUsers = courseUserAssignmentRepository.findByCourse_idOrderByUser_LastName(destinationCourseId)
                .stream()
                .map(CourseUserAssignment::getUser)
                .collect(Collectors.toSet());
        int assignmentCount = 0;
        for(CourseUserAssignment assignment : allAssignments){
            if(!alreadyAssignedUsers.contains(assignment.getUser())){
                CourseUserAssignmentPayload p = new CourseUserAssignmentPayload(assignment.getUser().getUserName(), destinationCourse.getCourseId());
                assignCourseToUser(p);
                assignmentCount ++;
            }
        }
            return new Feedback(List.of(assignmentCount + " Teilnehmer hinzugefügt."), Collections.emptyList(), Collections.emptyList());
    }

    @RequiredRole(Role.ADMIN_ROLE_NAME)
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
                .info(List.of("Die Kurszuordnung wurde entfernt"))
                .build();
    }


}
