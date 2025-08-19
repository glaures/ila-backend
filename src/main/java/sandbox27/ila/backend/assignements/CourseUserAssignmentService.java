package sandbox27.ila.backend.assignements;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sandbox27.ila.backend.course.CourseDto;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.infrastructure.security.AuthenticatedUser;

import java.util.List;
import java.util.Optional;

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
}
