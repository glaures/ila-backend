package sandbox27.ila.backend.assignments.algorithm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import sandbox27.ila.backend.assignments.AssignmentDeletionService;
import sandbox27.ila.backend.assignments.CourseUserAssignment;
import sandbox27.ila.backend.assignments.CourseUserAssignmentRepository;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseBlockAssignment;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.course.CourseCategory;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.exclusion.UserBlockExclusionService;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.preference.Preference;
import sandbox27.ila.backend.preference.PreferenceRepository;
import sandbox27.ila.backend.user.Role;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ab Klasse 11 werden zwei statt drei Kurse zugewiesen.
 */
class UpperSecondaryCourseCountTest {

    private static final long PERIOD_ID = 1L;

    private final PeriodRepository periodRepository = mock(PeriodRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CourseRepository courseRepository = mock(CourseRepository.class);
    private final BlockRepository blockRepository = mock(BlockRepository.class);
    private final PreferenceRepository preferenceRepository = mock(PreferenceRepository.class);
    private final CourseUserAssignmentRepository courseUserAssignmentRepository = mock(CourseUserAssignmentRepository.class);
    private final CourseBlockAssignmentRepository courseBlockAssignmentRepository = mock(CourseBlockAssignmentRepository.class);
    private final UserBlockExclusionService userBlockExclusionService = mock(UserBlockExclusionService.class);
    private final AssignmentResultRepository assignmentResultRepository = mock(AssignmentResultRepository.class);
    private final AssignmentDeletionService assignmentDeletionService = mock(AssignmentDeletionService.class);

    private final CourseAssignmentService service = new CourseAssignmentService(
            periodRepository, userRepository, courseRepository, blockRepository, preferenceRepository,
            courseUserAssignmentRepository, courseBlockAssignmentRepository, userBlockExclusionService,
            assignmentResultRepository, assignmentDeletionService);

    private final Period period = Period.builder().id(PERIOD_ID).name("Testphase").current(true).build();
    private final User middleSchooler = student("mia.mittelstufe", 7);
    private final User upperSecondary = student("olaf.oberstufe", 11);

    private final List<Block> blocks = new ArrayList<>();
    private final List<Course> courses = new ArrayList<>();
    private final List<CourseBlockAssignment> courseBlockAssignments = new ArrayList<>();
    private final List<Preference> preferences = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Vier Blöcke an vier Wochentagen – der Tageskonflikt begrenzt die Vergabe also nicht.
        // In jedem Block ein Kurs, die Kategorien wechseln, damit die Mindestzahl an
        // Kategorien für die Mittelstufe erfüllbar ist.
        CourseCategory[] categories = {CourseCategory.iLa, CourseCategory.BuE,
                CourseCategory.iLa, CourseCategory.BuE};
        DayOfWeek[] days = {DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY};
        for (int i = 0; i < days.length; i++) {
            Block block = Block.builder().id(i + 1).period(period).dayOfWeek(days[i])
                    .startTime(LocalTime.of(14, 0)).endTime(LocalTime.of(15, 0)).build();
            blocks.add(block);

            Course course = new Course();
            course.setId(i + 1);
            course.setName("Kurs " + (i + 1));
            course.setPeriod(period);
            course.setMaxAttendees(30);
            course.setGrades(Set.of(7, 11));
            course.setCourseCategories(Set.of(categories[i]));
            courses.add(course);
            courseBlockAssignments.add(CourseBlockAssignment.builder().course(course).block(block).build());

            // Beide Schüler setzen den Kurs jeweils als Erstwunsch
            preferences.add(Preference.builder().user(middleSchooler).block(block).course(course).preferenceIndex(0).build());
            preferences.add(Preference.builder().user(upperSecondary).block(block).course(course).preferenceIndex(0).build());
        }

        when(periodRepository.findById(PERIOD_ID)).thenReturn(Optional.of(period));
        when(userRepository.findAll()).thenReturn(List.of(middleSchooler, upperSecondary));
        when(courseRepository.findAllByPeriod(period)).thenReturn(courses);
        when(blockRepository.findByPeriod(period)).thenReturn(blocks);
        when(courseBlockAssignmentRepository.findAllByPeriodId(PERIOD_ID)).thenReturn(courseBlockAssignments);
        when(preferenceRepository.findAllByBlock_Period(period)).thenReturn(preferences);
        when(courseUserAssignmentRepository.findByCourse_Period(period)).thenReturn(List.of());
        when(userBlockExclusionService.getExcludedBlockIds(anyString(), anyLong())).thenReturn(Set.of());
        when(assignmentResultRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void upperSecondaryStudentsGetTwoCoursesOthersThree() {
        service.assignCourses(PERIOD_ID);

        assertEquals(3, savedCoursesFor(middleSchooler));
        assertEquals(2, savedCoursesFor(upperSecondary));
    }

    @Test
    void bothStudentsCountAsFullyAssigned() {
        AssignmentResult result = service.assignCourses(PERIOD_ID);

        // Der Oberstufenschüler gilt mit zwei Kursen als vollständig versorgt und darf
        // nicht als "teilweise zugewiesen" in der Statistik landen.
        assertEquals(2, result.getAssignedStudents());
        assertEquals(0, result.getPartiallyAssigned());
        assertEquals(0, result.getUnassigned());
    }

    @Test
    void aRerunDiscardsThePreviousAutomaticAssignmentsIncludingTheirExchangeRequests() {
        CourseUserAssignment previousRun = CourseUserAssignment.builder()
                .id(500L).user(middleSchooler).course(courses.get(0)).block(blocks.get(0)).preset(false).build();
        CourseUserAssignment byHand = CourseUserAssignment.builder()
                .id(501L).user(upperSecondary).course(courses.get(1)).block(blocks.get(1)).preset(true).build();
        when(courseUserAssignmentRepository.findByCourse_Period(period))
                .thenReturn(List.of(previousRun, byHand));

        service.assignCourses(PERIOD_ID);

        // Über den Deletion-Service, weil nur der die Wechselwünsche zu den verworfenen
        // Zuweisungen mit abräumt. Die Handzuweisung bleibt unangetastet.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CourseUserAssignment>> captor = ArgumentCaptor.forClass(List.class);
        verify(assignmentDeletionService).delete(captor.capture());
        assertEquals(List.of(previousRun), captor.getValue());
        verify(courseUserAssignmentRepository, never()).deleteAll(any());
    }

    @SuppressWarnings("unchecked")
    private long savedCoursesFor(User student) {
        ArgumentCaptor<List<CourseUserAssignment>> captor = ArgumentCaptor.forClass(List.class);
        verify(courseUserAssignmentRepository).saveAll(captor.capture());
        return captor.getValue().stream()
                .filter(a -> a.getUser().getUserName().equals(student.getUserName()))
                .count();
    }

    private static User student(String userName, int grade) {
        User user = User.builder()
                .userName(userName)
                .firstName("Vorname")
                .lastName("Nachname")
                .grade(grade)
                .ilaMember(true)
                .build();
        user.getRoles().add(Role.STUDENT);
        return user;
    }
}
