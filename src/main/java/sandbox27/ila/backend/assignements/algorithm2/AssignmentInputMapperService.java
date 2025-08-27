package sandbox27.ila.backend.assignements.algorithm2;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import sandbox27.ila.backend.assignements.CourseUserAssignment;
import sandbox27.ila.backend.assignements.algorithm2.dtos.*;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseBlockAssignment;
import sandbox27.ila.backend.preference.Preference;
import sandbox27.ila.backend.user.User;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssignmentInputMapperService {

    private final ModelMapper modelMapper;

    public AssignmentInputDTO buildInput(
            List<User> users,
            List<Block> blocks,
            List<Course> courses,
            List<CourseBlockAssignment> courseBlockAssignments,
            List<Preference> preferences,
            List<CourseUserAssignment> predefinedAssignments
    ) {
        AssignmentInputDTO input = new AssignmentInputDTO();

        input.setUsers(users.stream()
                .map(u -> modelMapper.map(u, UserDTO.class))
                .collect(Collectors.toList()));

        input.setBlocks(blocks.stream()
                .map(b -> modelMapper.map(b, BlockDTO.class))
                .collect(Collectors.toList()));

        Map<String, Long> coursesBlockIds = courseBlockAssignments.stream()
                .collect(Collectors.toMap(
                        cba -> cba.getCourse().getCourseId(),
                        cba -> cba.getBlock().getId()));

        input.setCourses(courses.stream()
                .map(c -> {
                    CourseDTO dto = modelMapper.map(c, CourseDTO.class);
                    Set<String> grades = c.getGrades().stream()
                            .map(String::valueOf)
                            .collect(Collectors.toSet());
                    dto.setBlockId(coursesBlockIds.get(c.getCourseId()));
                    return dto;
                })
                .collect(Collectors.toList()));

        input.setPreferences(preferences.stream()
                .map(p -> new PreferenceDTO(
                        p.getUser().getUserName(),
                        p.getBlock().getId(),
                        p.getCourse().getCourseId(),
                        p.getPreferenceIndex()
                ))
                .collect(Collectors.toList()));

        input.setPredefinedAssignments(predefinedAssignments.stream()
                .map(ass -> new CourseUserAssignmentDTO(
                        ass.getUser().getUserName(),
                        ass.getCourse().getCourseId(),
                        ass.getCourse().getName(),
                        ass.getBlock().getId(),
                        true,
                        false,
                        0
                ))
                .collect(Collectors.toList()));

        return input;
    }

}
