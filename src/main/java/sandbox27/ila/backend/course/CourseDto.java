package sandbox27.ila.backend.course;

import lombok.*;
import sandbox27.ila.backend.block.BlockDto;
import sandbox27.ila.backend.user.Gender;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserDto;

import java.util.HashSet;
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
    Set<CourseCategory> courseCategories = new HashSet<>();
    int maxAttendees;
    int minAttendees;
    Set<Integer> grades = new HashSet<>();
    boolean placeholder;
    String room;
    UserDto instructor;
    BlockDto block;
    Long blockId;
    Long periodId;
    boolean manualAssignmentOnly;
    Set<Gender> excludedGenders = new HashSet<>();

};
