package sandbox27.ila.backend.course;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CourseService {

    final CourseRepository courseRepository;

    @GetMapping
    public List<CourseDto> findAllCoursesForBlock(Long blockId) {
        return courseRepository.findAllByBlock_Id(blockId).stream()
                .map(c -> CourseDto.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .description(c.description)
                        .courseCategories(c.getCourseCategories())
                        .build())
                .toList();
    }

}
