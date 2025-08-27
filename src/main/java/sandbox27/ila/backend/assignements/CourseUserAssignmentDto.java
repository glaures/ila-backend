package sandbox27.ila.backend.assignements;

import lombok.*;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseDto;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseUserAssignmentDto {

    long courseId;
    CourseDto course;
    String userUserName;
    String firstName;
    String lastName;

    public CourseUserAssignmentDto(CourseUserAssignment courseUserAssignment, CourseDto course) {
        this.courseId = courseUserAssignment.getCourse().getId();
        this.course = course;
        this.userUserName = courseUserAssignment.getUser().getUserName();
        this.firstName = courseUserAssignment.getUser().getFirstName();
        this.lastName = courseUserAssignment.getUser().getLastName();
    }
}
