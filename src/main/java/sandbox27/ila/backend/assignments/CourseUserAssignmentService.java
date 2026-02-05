package sandbox27.ila.backend.assignments;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.block.BlockDto;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.course.CourseDto;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.course.events.CourseBlockChangedEvent;
import sandbox27.ila.backend.courseexclusions.CourseExclusionRepository;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodService;
import sandbox27.ila.backend.user.Role;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;
import sandbox27.ila.backend.user.events.UserCreatedEvent;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ErrorHandlingService;
import sandbox27.infrastructure.error.ServiceException;
import sandbox27.infrastructure.security.AuthenticatedUser;
import sandbox27.infrastructure.security.RequiredRole;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@Slf4j
@RequestMapping("/assignments")
@RequiredArgsConstructor
public class CourseUserAssignmentService {

    private final CourseUserAssignmentRepository courseUserAssignmentRepository;
    private final ModelMapper modelMapper;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final BlockRepository blockRepository;
    private final CourseBlockAssignmentRepository courseBlockAssignmentRepository;
    private final CourseExclusionRepository courseExclusionRepository;
    private final PeriodService periodService;
    private final ErrorHandlingService errorHandlingService;

    @GetMapping("/{blockId}")
    @Transactional
    public CourseUserAssignmentResponse getCourseUserAssignment(@PathVariable("blockId") Long blockId,
                                                                @AuthenticatedUser User authenticatedUser) {
        List<CourseUserAssignment> assignments = courseUserAssignmentRepository.findByUserAndBlock_Id(authenticatedUser, blockId);
        if (assignments.size() > 1) {
            errorHandlingService.handleWarning(authenticatedUser.getUserName() + " hat mehr als einen Block assigned. Nehme den ersten.");
        }
        if (assignments.isEmpty()) return new CourseUserAssignmentResponse();
        CourseUserAssignment assignment = assignments.getFirst();
        CourseDto courseDto = modelMapper.map(assignment.getCourse(), CourseDto.class);
        BlockDto blockDto = modelMapper.map(assignment.getBlock(), BlockDto.class);
        return new CourseUserAssignmentResponse(new CourseUserAssignmentDto(assignment, courseDto, blockDto));
    }

    /**
     * Gibt alle Kurszuweisungen des eingeloggten Benutzers für eine Periode zurück.
     * Wird für die Wechselrunde verwendet.
     */
    @GetMapping("/my")
    @Transactional
    @RequiredRole(Role.STUDENT_ROLE_NAME)
    public List<CourseUserAssignmentDto> getMyAssignments(
            @AuthenticatedUser User authenticatedUser,
            @RequestParam("periodId") Long periodId) {

        List<CourseUserAssignment> assignments = courseUserAssignmentRepository
                .findByUser_userNameAndCourse_Period_Id(authenticatedUser.getUserName(), periodId);

        return assignments.stream()
                .map(a -> {
                    CourseDto courseDto = modelMapper.map(a.getCourse(), CourseDto.class);
                    // BlockDto manuell erstellen, um dayOfWeek korrekt als String zu haben
                    BlockDto blockDto = BlockDto.builder()
                            .id(a.getBlock().getId())
                            .name(a.getBlock().getName())
                            .dayOfWeek(a.getBlock().getDayOfWeek() != null
                                    ? a.getBlock().getDayOfWeek().name()
                                    : null)
                            .startTime(a.getBlock().getStartTime())
                            .endTime(a.getBlock().getEndTime())
                            .build();
                    return new CourseUserAssignmentDto(a, courseDto, blockDto);
                })
                .toList();
    }

    @GetMapping
    @Transactional
    public List<CourseUserAssignmentDto> getCourseUserAssignment(@RequestParam(value = "course-id", required = false) Long courseId,
                                                                 @RequestParam(value = "user-name", required = false) String userName,
                                                                 @RequestParam(value = "period-id", required = false) Long periodId) {
        List<CourseUserAssignment> assignments = Collections.emptyList();
        if (courseId != null) {
            assignments = courseUserAssignmentRepository.findByCourse_idOrderByUser_LastName(courseId);
        } else if (userName != null) {
            if (periodId == null)
                throw new ServiceException(ErrorCode.FieldRequired, "Phase");
            assignments = courseUserAssignmentRepository.findByUser_userNameAndCourse_Period_Id(userName, periodId);
        }
        return assignments
                .stream()
                .map(a -> {
                    return new CourseUserAssignmentDto(a, modelMapper.map(a.getCourse(), CourseDto.class), modelMapper.map(a.getBlock(), BlockDto.class));
                })
                .toList();
    }

    public record CourseUserAssignmentPayload(
            String userName,
            String courseId
    ) {
    }

    @RequiredRole({Role.ADMIN_ROLE_NAME, Role.COURSE_INSTRUCTOR_ROLE_NAME})
    @Transactional
    @PostMapping
    public Feedback assignCourseToUser(@RequestBody CourseUserAssignmentPayload courseUserAssignmentPayload) throws ServiceException {
        final Period period = periodService.getCurrent();
        User user = userRepository.findById(courseUserAssignmentPayload.userName)
                .orElseThrow(() -> new ServiceException(ErrorCode.UserNotFound));
        Course course = courseRepository.findByCourseIdAndPeriod(courseUserAssignmentPayload.courseId, period)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));

        // Prüfen, ob der Benutzer von diesem Kurs ausgeschlossen ist
        if (courseExclusionRepository.existsByCourseAndUser(course, user)) {
            throw new ServiceException(ErrorCode.UserExcludedFromCourse, user.getFirstName() + " " + user.getLastName(), course.getName());
        }

        Block block = courseBlockAssignmentRepository.findAllByCourse(course).getFirst().getBlock();

        // Prüfen, ob der Benutzer bereits eine Zuweisung am selben Tag in dieser Periode hat
        Optional<CourseUserAssignment> existingAssignment = courseUserAssignmentRepository
                .findByUserAndBlock_DayOfWeekAndBlock_Period(user, block.getDayOfWeek(), period);

        if (existingAssignment.isPresent()) {
            throw new ServiceException(ErrorCode.UserAlreadyHasCourseAssignedThatDay,
                    existingAssignment.get().getCourse().getName());
        }

        CourseUserAssignment courseUserAssignment = CourseUserAssignment.builder()
                .user(user)
                .course(course)
                .block(block)
                .preset(true)
                .build();
        courseUserAssignmentRepository.save(courseUserAssignment);
        return Feedback.builder()
                .info(List.of("Zuweisung gespeichert."))
                .build();
    }

    @RequiredRole({Role.ADMIN_ROLE_NAME, Role.COURSE_INSTRUCTOR_ROLE_NAME})
    @Transactional
    @PostMapping("/copy-assignments")
    public Feedback copyAssignments(@RequestParam("source-course-id") Long sourceCourseId,
                                    @RequestParam("destination-course-id") Long destinationCourseId) throws ServiceException {
        Course destinationCourse = courseRepository.getReferenceById(destinationCourseId);
        List<CourseUserAssignment> allAssignments = courseUserAssignmentRepository.findByCourse_idOrderByUser_LastName(sourceCourseId);
        Set<User> alreadyAssignedUsers = courseUserAssignmentRepository.findByCourse_idOrderByUser_LastName(destinationCourseId)
                .stream()
                .map(CourseUserAssignment::getUser)
                .collect(Collectors.toSet());

        // Ausgeschlossene Benutzer für den Zielkurs ermitteln
        Set<String> excludedUserNames = courseExclusionRepository.findAllByCourseId(destinationCourseId)
                .stream()
                .map(e -> e.getUser().getUserName())
                .collect(Collectors.toSet());

        int assignmentCount = 0;
        int skippedCount = 0;
        for (CourseUserAssignment assignment : allAssignments) {
            if (!alreadyAssignedUsers.contains(assignment.getUser())) {
                // Ausgeschlossene Benutzer überspringen
                if (excludedUserNames.contains(assignment.getUser().getUserName())) {
                    skippedCount++;
                    log.info("Überspringe ausgeschlossenen Benutzer {} für Kurs {}",
                            assignment.getUser().getUserName(), destinationCourse.getName());
                    continue;
                }
                CourseUserAssignmentPayload p = new CourseUserAssignmentPayload(assignment.getUser().getUserName(), destinationCourse.getCourseId());
                assignCourseToUser(p);
                assignmentCount++;
            }
        }

        List<String> infos = new java.util.ArrayList<>();
        infos.add(assignmentCount + " Teilnehmer hinzugefügt.");
        if (skippedCount > 0) {
            infos.add(skippedCount + " ausgeschlossene Teilnehmer übersprungen.");
        }
        return new Feedback(infos, Collections.emptyList(), Collections.emptyList());
    }

    @RequiredRole({Role.ADMIN_ROLE_NAME, Role.COURSE_INSTRUCTOR_ROLE_NAME})
    @Transactional
    @DeleteMapping("/{assignmentId}")
    public Feedback deleteAssignment(@PathVariable("assignmentId") Long assignmentId,
                                     @AuthenticatedUser User authenticatedUser) throws ServiceException {
        CourseUserAssignment assignment = courseUserAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));
        courseUserAssignmentRepository.delete(assignment);
        return Feedback.builder()
                .info(List.of("Die Kurszuordnung wurde entfernt"))
                .build();
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCourseBlockChange(CourseBlockChangedEvent courseBlockChangedEvent) {
        List<CourseUserAssignment> assignmentsForMovedCourse = courseUserAssignmentRepository.findByCourse_idOrderByUser_LastName(courseBlockChangedEvent.courseId());
        final Block newBlock = blockRepository.getReferenceById(courseBlockChangedEvent.newBlockId());
        for (CourseUserAssignment assignment : assignmentsForMovedCourse) {
            assignment.setBlock(newBlock);
            courseUserAssignmentRepository.save(assignment);
        }
    }


}