package sandbox27.ila.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import sandbox27.ila.backend.assignements.CourseUserAssignment;
import sandbox27.ila.backend.assignements.CourseUserAssignmentRepository;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

@Component
@Order(66)
public class CourseUserAssignmentImporter {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CourseUserAssignmentRepository assignmentRepository;
    private final ObjectMapper objectMapper;

    public CourseUserAssignmentImporter(CourseRepository courseRepository,
                                        UserRepository userRepository,
                                        CourseUserAssignmentRepository assignmentRepository,
                                        ObjectMapper objectMapper) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.objectMapper = objectMapper;
    }

    public void runImport() throws IOException {
        InputStream inputStream = new ClassPathResource("course_assignments.json").getInputStream();
        List<AssignmentRecord> records = objectMapper.readValue(
                inputStream,
                new TypeReference<>() {
                }
        );

        for (AssignmentRecord record : records) {
            Optional<Course> courseOpt = courseRepository.findByCourseId(record.courseId());
            Optional<User> userOpt = userRepository.findByInternalId(record.userInternalId());

            if (courseOpt.isEmpty()) {
                System.err.println("Course not found: " + record.courseId());
                continue;
            }
            if (userOpt.isEmpty()) {
                System.err.println("User not found: " + record.userInternalId());
                continue;
            }

            CourseUserAssignment assignment = new CourseUserAssignment();
            assignment.setCourse(courseOpt.get());
            assignment.setUser(userOpt.get());

            assignmentRepository.save(assignment);
        }
    }

    // DTO für JSON-Einträge
    public record AssignmentRecord(String courseId, String userInternalId) {
    }
}
