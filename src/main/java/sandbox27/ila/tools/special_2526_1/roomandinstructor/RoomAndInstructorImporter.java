package sandbox27.ila.tools.special_2526_1.roomandinstructor;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;
import sandbox27.ila.tools.special_2526_1.theater.TheaterBelegung;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RoomAndInstructorImporter {

    public record RoomAndInstructor(String courseId, String room, String instructor) {}

    final CourseRepository courseRepository;
    final UserRepository userRepository;

    @Transactional
    public void runImport() throws IOException {
        List<RoomAndInstructor> importedRnI = importFromFile();
        for (RoomAndInstructor rnI : importedRnI) {
            try {
                Optional<Course> courseOptional = courseRepository.findByCourseId(rnI.courseId);
                if(courseOptional.isPresent()) {
                    Course course = courseOptional.get();
                    course.setRoom(rnI.room);
                    Optional<User> userOpt = userRepository.findByLastName(rnI.instructor);
                    if(userOpt.isPresent()) {
                        course.setInstructor(userOpt.get());
                    } else {
                        System.err.println("Instructor not found: " + rnI.instructor);
                    }
                    courseRepository.save(course);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    public List<RoomAndInstructor> importFromFile() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS.mappedFeature());
        InputStream inputStream = new ClassPathResource("room_instructor.json").getInputStream();
        return new ArrayList<>(objectMapper.readValue(inputStream, new TypeReference<List<RoomAndInstructor>>() {
        }));
    }

}
