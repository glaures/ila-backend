package sandbox27.ila.tools.special_2526_1.theater;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import sandbox27.ila.backend.assignments.CourseUserAssignment;
import sandbox27.ila.backend.assignments.CourseUserAssignmentRepository;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Component
@RequiredArgsConstructor
public class TheaterBelegung {

    public record Assignment(String courseId, String firstName, String lastName) {
    }

    final UserRepository userRepository;
    final CourseRepository courseRepository;
    final CourseBlockAssignmentRepository courseBlockAssignmentRepository;
    final CourseUserAssignmentRepository courseUserAssignmentRepository;

    @Transactional
    public void runImport() throws IOException {
        List<Assignment> importedAssignments = importFromFile();
        for (Assignment importedAssignment : importedAssignments) {
            try {
                storeImportedAssignment(importedAssignment);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    @Transactional
    public void storeImportedAssignment(Assignment assignment) throws IOException {
        User user = userRepository.findByFirstNameAndLastName(assignment.firstName, assignment.lastName)
                .orElseThrow(() -> new IOException("can not find user " + assignment.firstName + " " + assignment.lastName));
        Course course = courseRepository.findByCourseId(assignment.courseId)
                .orElseThrow(() -> new IOException("can not find course " + assignment.courseId));
        Block block = courseBlockAssignmentRepository.findAllByCourse(course).get(0).getBlock();
        courseUserAssignmentRepository.findByCourseAndUser(course, user)
                .ifPresentOrElse(existing -> {
                        },
                        () -> {
                            courseUserAssignmentRepository.save(CourseUserAssignment.builder()
                                    .course(course)
                                    .user(user)
                                    .block(block)
                                    .build());
                        });
    }

    public List<Assignment> importFromFile() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        InputStream inputStream = new ClassPathResource("theater.json").getInputStream();
        return new ArrayList<>(objectMapper.readValue(inputStream, new TypeReference<List<Assignment>>() {
        }));
    }

}
