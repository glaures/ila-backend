package sandbox27.ila.backend.assignments;

import lombok.*;
import sandbox27.ila.backend.block.BlockDto;
import sandbox27.ila.backend.course.CourseDto;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseUserAssignmentDto {

    long id;
    long courseId;
    CourseDto course;
    String userUserName;
    String firstName;
    String lastName;

    public CourseUserAssignmentDto(CourseUserAssignment courseUserAssignment, CourseDto course, BlockDto block) {
        this.id = courseUserAssignment.getId();
        this.courseId = courseUserAssignment.getCourse().getId();
        this.course = course;
        this.course.setBlock(block);
        this.userUserName = courseUserAssignment.getUser().getUserName();
        this.firstName = courseUserAssignment.getUser().getFirstName();
        this.lastName = courseUserAssignment.getUser().getLastName();
    }
}
