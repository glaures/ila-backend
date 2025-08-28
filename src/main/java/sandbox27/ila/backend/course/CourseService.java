package sandbox27.ila.backend.course;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/courses")
public class CourseService {

    final CourseRepository courseRepository;
    final ModelMapper modelMapper;

    @GetMapping
    public List<CourseDto> findAllCoursesForBlock(@RequestParam(name = "block-id", required = false) Long blockId,
                                                  @RequestParam(required = false, defaultValue = "0") int grade) {
        if (blockId == null) {
            return courseRepository.findAll().stream().map(c -> modelMapper.map(c, CourseDto.class)).toList();
        } else
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
