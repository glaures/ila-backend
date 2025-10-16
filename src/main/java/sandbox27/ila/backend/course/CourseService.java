package sandbox27.ila.backend.course;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.assignements.CourseUserAssignmentRepository;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.block.BlockService;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.preference.PreferenceRepository;
import sandbox27.ila.backend.user.Role;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;
import sandbox27.infrastructure.security.RequiredRole;

import java.util.List;

@RestController
@Transactional
@RequiredArgsConstructor
@RequestMapping("/courses")
public class CourseService {

    final CourseRepository courseRepository;
    final BlockService blockService;
    private final CourseBlockAssignmentRepository courseBlockAssignmentRepository;
    private final BlockRepository blockRepository;
    private final PeriodRepository periodRepository;
    private final CourseUserAssignmentRepository courseUserAssignmentRepository;
    private final PreferenceRepository preferenceRepository;

    @GetMapping
    public List<CourseDto> getCourses(@RequestParam(name = "block-id", required = false) Long blockId,
                                      @RequestParam(required = false, defaultValue = "0") int grade,
                                      @RequestParam(value = "period-id", required = false) Long periodId) {
        if (periodId != null) {
            return courseRepository.findAllByPeriod_Id(periodId).stream()
                    .map(this::map)
                    .toList();
        } else if (blockId == null)
            return courseRepository.findAll().stream()
                    .map(this::map)
                    .toList();
        else
            return courseRepository.findAllByBlock_Id(blockId).stream()
                    .filter(course -> grade == 0 || course.getGrades().contains(grade))
                    .map(this::map)
                    .toList();
    }

    @GetMapping("/{courseId}")
    public CourseDto getCourse(@PathVariable Long courseId) throws ServiceException {
        return map(courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound)));
    }

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @PostMapping
    public CourseDto createCourse(@RequestBody CourseDto courseDto) {
        Course course = new Course();
        map(courseDto, course);
        course.setPeriod(periodRepository.findById(courseDto.getPeriodId()).orElseThrow(() -> new ServiceException(ErrorCode.NotFound)));
        return map(courseRepository.save(course));
    }

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @PutMapping("/{id}")
    public CourseDto updateCourse(@PathVariable Long id, @RequestBody CourseDto courseDto) throws ServiceException {
        Course course = courseRepository.findById(id).orElseThrow(() -> new ServiceException(ErrorCode.NotFound));
        map(courseDto, course);
        return map(course);
    }

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @DeleteMapping("/{id}")
    public void deleteCourse(@PathVariable Long id) throws ServiceException {
        Course course = courseRepository.findById(id).orElseThrow(() -> new ServiceException(ErrorCode.NotFound));
        // Kurszuweisungen zuerst löschen
        courseBlockAssignmentRepository.deleteByCourse(course);
        courseUserAssignmentRepository.deleteByCourse(course);
        preferenceRepository.deleteByCourse(course);
        courseRepository.delete(course);
    }

    private CourseDto map(Course c) {
        CourseDto res = CourseDto.builder()
                .id(c.getId())
                .courseId(c.getCourseId())
                .name(c.getName())
                .description(c.description)
                .courseCategories(c.getCourseCategories())
                .maxAttendees(c.getMaxAttendees())
                .minAttendees(c.getMinAttendees())
                .room(c.getRoom())
                .instructor(c.getInstructor())
                .grades(c.getGrades())
                .placeholder(c.placeholder)
                .build();
        res.setBlock(blockService.getBlockByCourseId(res.getId()).orElse(null));
        return res;
    }

    public void map(CourseDto courseDto, Course course) {
        course.setCourseId(courseDto.courseId);
        course.setDescription(courseDto.description);
        course.setName(courseDto.name);
        course.setDescription(courseDto.description);
        course.setInstructor(courseDto.instructor);
        course.getCourseCategories().clear();
        course.getCourseCategories().addAll(courseDto.courseCategories);
        course.setMaxAttendees(courseDto.getMaxAttendees());
        course.setMinAttendees(courseDto.getMinAttendees());
        course.getGrades().clear();
        course.getGrades().addAll(courseDto.grades);
        course.setPlaceholder(courseDto.placeholder);
        course.setRoom(courseDto.room);
        courseRepository.save(course);
        // Block Zuweisung fehlt noch
        if (courseDto.getBlockId() != null) {
            List<CourseBlockAssignment> courseBlockAssignments = courseBlockAssignmentRepository.findAllByCourse(course);
            courseBlockAssignmentRepository.deleteAll(courseBlockAssignments);
            Block block = blockRepository.findById(courseDto.getBlockId()).orElseThrow(() -> new ServiceException(ErrorCode.NotFound));
            courseBlockAssignmentRepository.save(
                    CourseBlockAssignment.builder()
                            .course(course)
                            .block(block)
                            .build()
            );
        }
    }

}
