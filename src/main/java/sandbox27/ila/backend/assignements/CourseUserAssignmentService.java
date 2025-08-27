package sandbox27.ila.backend.assignements;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.assignements.algorithm2.dtos.UserDTO;
import sandbox27.ila.backend.course.CourseDto;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserDto;
import sandbox27.ila.infrastructure.security.AuthenticatedUser;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@Service
@RequestMapping("/assignments")
@RequiredArgsConstructor
public class CourseUserAssignmentService {

    final CourseUserAssignmentRepository courseUserAssignmentRepository;
    final ModelMapper modelMapper;

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
}
