package sandbox27.ila.backend.courseexclusions;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
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
@RequestMapping("/course-exclusions")
public class CourseExclusionController {

    private final CourseExclusionRepository courseExclusionRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final PeriodRepository periodRepository;

    /**
     * Alle Ausschlüsse für einen bestimmten Kurs abrufen
     */
    @GetMapping("/by-course/{courseId}")
    @RequiredRole({Role.ADMIN_ROLE_NAME, Role.COURSE_INSTRUCTOR_ROLE_NAME})
    public List<CourseExclusionDto> getExclusionsByCourse(
            @PathVariable Long courseId,
            @AuthenticatedUser User currentUser) {

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));

        verifyAccessToCourse(course, currentUser);

        return courseExclusionRepository.findAllByCourseId(courseId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Alle Ausschlüsse für die aktuelle Periode abrufen (nur ADMIN)
     */
    @GetMapping("/by-period")
    @RequiredRole(Role.ADMIN_ROLE_NAME)
    public List<CourseExclusionDto> getExclusionsByCurrentPeriod() {
        Period currentPeriod = periodRepository.findByCurrent(true)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));

        return courseExclusionRepository.findAllByPeriodId(currentPeriod.getId()).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Alle Ausschlüsse für einen bestimmten Schüler in der aktuellen Periode
     */
    @GetMapping("/by-user/{userName}")
    @RequiredRole(Role.ADMIN_ROLE_NAME)
    public List<CourseExclusionDto> getExclusionsByUser(@PathVariable String userName) {
        Period currentPeriod = periodRepository.findByCurrent(true)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));

        return courseExclusionRepository.findAllByPeriodIdAndUserName(currentPeriod.getId(), userName).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Neuen Kursausschluss erstellen
     */
    @PostMapping
    @RequiredRole({Role.ADMIN_ROLE_NAME, Role.COURSE_INSTRUCTOR_ROLE_NAME})
    public CourseExclusionDto createExclusion(
            @RequestBody CreateCourseExclusionRequest request,
            @AuthenticatedUser User currentUser) {

        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));

        verifyAccessToCourse(course, currentUser);

        User userToExclude = userRepository.findById(request.userName())
                .orElseThrow(() -> new ServiceException(ErrorCode.UserNotFound));

        // Prüfen, ob bereits ein Ausschluss existiert
        if (courseExclusionRepository.existsByCourseAndUser(course, userToExclude)) {
            throw new ServiceException(ErrorCode.UserAlreadyExists, request.userName());
        }

        CourseExclusion exclusion = CourseExclusion.builder()
                .course(course)
                .user(userToExclude)
                .reason(request.reason())
                .createdBy(currentUser)
                .build();

        return mapToDto(courseExclusionRepository.save(exclusion));
    }

    /**
     * Mehrere Kursausschlüsse auf einmal erstellen
     */
    @PostMapping("/bulk")
    @RequiredRole({Role.ADMIN_ROLE_NAME, Role.COURSE_INSTRUCTOR_ROLE_NAME})
    public List<CourseExclusionDto> createExclusionsBulk(
            @RequestBody List<CreateCourseExclusionRequest> requests,
            @AuthenticatedUser User currentUser) {

        return requests.stream()
                .map(request -> {
                    try {
                        return createExclusion(request, currentUser);
                    } catch (ServiceException e) {
                        // Bei UserAlreadyExists überspringen wir den Eintrag
                        if (e.getErrorCode() == ErrorCode.UserAlreadyExists) {
                            return null;
                        }
                        throw e;
                    }
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());
    }

    /**
     * Grund für einen Ausschluss aktualisieren
     */
    @PutMapping("/{exclusionId}")
    @RequiredRole({Role.ADMIN_ROLE_NAME, Role.COURSE_INSTRUCTOR_ROLE_NAME})
    public CourseExclusionDto updateExclusion(
            @PathVariable Long exclusionId,
            @RequestBody CreateCourseExclusionRequest request,
            @AuthenticatedUser User currentUser) {

        CourseExclusion exclusion = courseExclusionRepository.findById(exclusionId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));

        verifyAccessToCourse(exclusion.getCourse(), currentUser);

        exclusion.setReason(request.reason());

        return mapToDto(courseExclusionRepository.save(exclusion));
    }

    /**
     * Kursausschluss löschen
     */
    @DeleteMapping("/{exclusionId}")
    @RequiredRole({Role.ADMIN_ROLE_NAME, Role.COURSE_INSTRUCTOR_ROLE_NAME})
    public void deleteExclusion(
            @PathVariable Long exclusionId,
            @AuthenticatedUser User currentUser) {

        CourseExclusion exclusion = courseExclusionRepository.findById(exclusionId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));

        verifyAccessToCourse(exclusion.getCourse(), currentUser);

        courseExclusionRepository.delete(exclusion);
    }

    /**
     * Kursausschluss per Kurs-ID und Benutzer-ID löschen
     */
    @DeleteMapping("/by-course/{courseId}/user/{userName}")
    @RequiredRole({Role.ADMIN_ROLE_NAME, Role.COURSE_INSTRUCTOR_ROLE_NAME})
    public void deleteExclusionByCourseAndUser(
            @PathVariable Long courseId,
            @PathVariable String userName,
            @AuthenticatedUser User currentUser) {

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));

        verifyAccessToCourse(course, currentUser);

        User user = userRepository.findById(userName)
                .orElseThrow(() -> new ServiceException(ErrorCode.UserNotFound));

        courseExclusionRepository.deleteByCourseAndUser(course, user);
    }

    /**
     * Prüft, ob ein Benutzer für einen Kurs ausgeschlossen ist
     */
    @GetMapping("/check/{courseId}/{userName}")
    public boolean isUserExcluded(
            @PathVariable Long courseId,
            @PathVariable String userName) {

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));

        User user = userRepository.findById(userName)
                .orElseThrow(() -> new ServiceException(ErrorCode.UserNotFound));

        return courseExclusionRepository.existsByCourseAndUser(course, user);
    }

    /**
     * Prüft, ob der aktuelle Benutzer Zugriff auf den Kurs hat.
     * ADMIN hat immer Zugriff, COURSE_INSTRUCTOR nur auf eigene Kurse.
     */
    private void verifyAccessToCourse(Course course, User currentUser) {
        if (currentUser.hasRole(Role.ADMIN_ROLE_NAME)) {
            return; // Admin hat immer Zugriff
        }

        if (currentUser.hasRole(Role.COURSE_INSTRUCTOR_ROLE_NAME)) {
            // Kursleiter darf nur eigene Kurse verwalten
            if (course.getInstructor() == null ||
                !course.getInstructor().getUserName().equals(currentUser.getUserName())) {
                throw new ServiceException(ErrorCode.AccessDenied);
            }
            return;
        }

        throw new ServiceException(ErrorCode.AccessDenied);
    }

    private CourseExclusionDto mapToDto(CourseExclusion exclusion) {
        return new CourseExclusionDto(
                exclusion.getId(),
                exclusion.getCourse().getId(),
                exclusion.getCourse().getName(),
                UserDto.map(exclusion.getUser()),
                exclusion.getReason(),
                exclusion.getCreatedBy() != null ? UserDto.map(exclusion.getCreatedBy()) : null,
                exclusion.getCreatedAt()
        );
    }
}
