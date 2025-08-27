package sandbox27.ila.backend.course;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/courses")
public class CourseService {

    final CourseRepository courseRepository;

    @GetMapping
    public List<CourseDto> findAllCoursesForBlock(@RequestParam(name = "block-id") Long blockId,
                                                  @RequestParam(required = false, defaultValue = "0") int grade) {
        return courseRepository.findAllByBlock_Id(blockId).stream()
                .filter(course -> grade == 0 || course.getGrades().contains(grade))
                .map(c -> CourseDto.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .description(c.description)
                        .courseCategories(c.getCourseCategories())
                        .maxAttendees(c.getMaxAttendees())
                        .room(c.getRoom())
                        .instructor(c.getInstructor())
                        .build())
                .toList();
    }

}
