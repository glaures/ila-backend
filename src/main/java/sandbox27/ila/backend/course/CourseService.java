package sandbox27.ila.backend.course;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.assignments.CourseUserAssignmentRepository;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.block.BlockService;
import sandbox27.ila.backend.courseexclusions.CourseExclusionRepository;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.preference.PreferenceRepository;
import sandbox27.ila.backend.user.*;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;
import sandbox27.infrastructure.security.AuthenticatedUser;
import sandbox27.infrastructure.security.RequiredRole;

import java.util.List;
import java.util.stream.Collectors;

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
    private final UserManagementService userManagementService;
    private final UserRepository userRepository;
    private final CourseExclusionRepository courseExclusionRepository;

    @GetMapping
    public List<CourseDto> getCourses(@RequestParam(name = "block-id", required = false) Long blockId,
                                      @RequestParam(value = "period-id", required = false) Long periodId,
                                      @AuthenticatedUser User user) {
        if (periodId != null && blockId == null) {
            return courseRepository.findAllByPeriod_Id(periodId).stream()
                    .map(this::map)
                    .collect(Collectors.toList());
        } else {
            List<Course> allCoursesInBlock = courseRepository.findAllByBlock_Id(blockId);
            if (!user.getRoles().contains(Role.ADMIN))
                return allCoursesInBlock.stream()
                        .filter(course -> !course.manualAssignmentOnly
                                && course.getGrades().contains(user.getGrade())
                                && !course.getExcludedGenders().contains(user.getGender()))
                        .map(this::map)
                        .toList();
            else
                return allCoursesInBlock.stream().map(this::map).toList();
        }
    }

    @GetMapping("/instructedbyme")
    @RequiredRole(Role.COURSE_INSTRUCTOR_ROLE_NAME)
    public List<CourseDto> getCoursesInstructedByMe(@AuthenticatedUser User user) {
        Period currentPeriod = periodRepository.findByCurrent(true).orElseThrow(() -> new ServiceException(ErrorCode.NotFound));
        return courseRepository.findByInstructorAndPeriod(user, currentPeriod).stream().map(this::map).toList();
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
        // Abhängige Daten zuerst löschen
        courseBlockAssignmentRepository.deleteByCourse(course);
        courseUserAssignmentRepository.deleteByCourse(course);
        preferenceRepository.deleteByCourse(course);
        courseExclusionRepository.deleteByCourseId(id);
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
                .instructor(UserDto.map(c.getInstructor()))
                .grades(c.getGrades())
                .placeholder(c.placeholder)
                .excludedGenders(c.excludedGenders)
                .manualAssignmentOnly(c.isManualAssignmentOnly())
                .build();
        res.setBlock(blockService.getBlockByCourseId(res.getId()).orElse(null));
        return res;
    }

    public void map(CourseDto courseDto, Course course) {
        course.setCourseId(courseDto.courseId);
        course.setDescription(courseDto.description);
        course.setName(courseDto.name);
        course.setDescription(courseDto.description);

        // Handle instructor - can be null
        if (courseDto.instructor != null && courseDto.instructor.getUserName() != null) {
            course.setInstructor(userRepository.findById(courseDto.instructor.getUserName()).orElse(null));
        } else {
            course.setInstructor(null);
        }

        course.getCourseCategories().clear();
        course.getCourseCategories().addAll(courseDto.courseCategories);
        course.setMaxAttendees(courseDto.getMaxAttendees());
        course.setMinAttendees(courseDto.getMinAttendees());
        course.getGrades().clear();
        course.getGrades().addAll(courseDto.grades);
        course.setPlaceholder(courseDto.placeholder);
        course.setRoom(courseDto.room);
        course.getExcludedGenders().clear();
        course.getExcludedGenders().addAll(courseDto.excludedGenders);
        course.setManualAssignmentOnly(courseDto.manualAssignmentOnly);
        courseRepository.save(course);
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