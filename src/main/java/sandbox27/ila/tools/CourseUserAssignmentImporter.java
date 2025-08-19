package sandbox27.ila.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import sandbox27.ila.backend.assignements.CourseUserAssignment;
import sandbox27.ila.backend.assignements.CourseUserAssignmentRepository;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseBlockAssignment;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

@Component
@Order(66)
@RequiredArgsConstructor
public class CourseUserAssignmentImporter {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CourseUserAssignmentRepository assignmentRepository;
    private final CourseBlockAssignmentRepository blockAssignmentRepository;
    private final ObjectMapper objectMapper;
    private final CourseBlockAssignmentRepository courseBlockAssignmentRepository;
    private final CourseUserAssignmentRepository courseUserAssignmentRepository;

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

            Optional<CourseUserAssignment> alreadyAssigned = courseUserAssignmentRepository.findByCourseAndUser(courseOpt.get(), userOpt.get());
            if (alreadyAssigned.isEmpty()) {
                List<CourseBlockAssignment> blocksOfCourse = courseBlockAssignmentRepository.findAllByCourse(courseOpt.get());
                if (blocksOfCourse.size() > 1) {
                    System.err.println("Course " + courseOpt.get().getName() + " has multiple blocks assigned. Taking first one for assignemnt of user.");
                } else if (blocksOfCourse.isEmpty()) {
                    throw new IOException("Course " + courseOpt.get().getName() + " has no blocks assigned.");
                }
                CourseUserAssignment assignment = new CourseUserAssignment();
                assignment.setCourse(courseOpt.get());
                assignment.setUser(userOpt.get());
                assignment.setBlock(blocksOfCourse.get(0).getBlock());

                assignmentRepository.save(assignment);
            }
        }
    }

    // DTO für JSON-Einträge
    public record AssignmentRecord(String courseId, String userInternalId) {
    }
}
