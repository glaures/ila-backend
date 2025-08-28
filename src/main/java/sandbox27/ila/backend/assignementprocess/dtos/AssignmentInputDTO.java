package sandbox27.ila.backend.assignementprocess.dtos;

import lombok.Data;

import java.util.List;

@Data
public class AssignmentInputDTO {

    private List<UserDTO> users;
    private List<BlockDTO> blocks;
    private List<CourseDTO> courses;
    private List<PreferenceDTO> preferences;
    private List<CourseUserAssignmentDTO> predefinedAssignments;

}
