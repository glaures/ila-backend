package sandbox27.ila.backend.course;

import lombok.*;
import sandbox27.ila.backend.user.User;

import java.util.Set;

@Builder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CourseDto {

    long id;
    String courseId;
    String name;
    String description;
    Set<CourseCategory> courseCategories;
    int maxAttendees;
    String room;
    String instructor;

};
