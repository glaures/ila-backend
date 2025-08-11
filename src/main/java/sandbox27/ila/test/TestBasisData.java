package sandbox27.ila.test;

import lombok.Data;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseBlockAssignment;
import sandbox27.ila.backend.user.User;

import java.util.List;

@Data
public class TestBasisData {

    List<Course> courses;
    List<Block> blocks;
    List<User> users;
    List<CourseBlockAssignment> courseBlockAssignments;
}
