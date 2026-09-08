package sandbox27.ila.backend.assignable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sandbox27.ila.backend.assignments.CourseUserAssignment;
import sandbox27.ila.backend.assignments.CourseUserAssignmentRepository;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseBlockAssignment;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.courseexclusions.CourseExclusionRepository;
import sandbox27.ila.backend.exchange.CourseEligibilityService;
import sandbox27.ila.backend.exclusion.UserBlockExclusionService;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.user.Gender;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Welche Kurse der Verwaltung für eine manuelle Zuweisung angeboten werden.
 * <p>
 * Entscheidend ist die Trennung: Kurse, die für den Schüler nie in Frage kommen, fehlen ganz;
 * Kurse, die im Moment nicht passen (Tageskonflikt, Kontingent), stehen mit Grund in der Liste,
 * damit die Verwaltung sieht, was der Zuweisung im Weg steht.
 */
class AssignableCoursesServiceTest {

    private static final long PERIOD_ID = 7L;
    private static final String USER_NAME = "max.muster";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PeriodRepository periodRepository = mock(PeriodRepository.class);
    private final CourseRepository courseRepository = mock(CourseRepository.class);
    private final CourseUserAssignmentRepository assignmentRepository = mock(CourseUserAssignmentRepository.class);
    private final CourseBlockAssignmentRepository courseBlockAssignmentRepository = mock(CourseBlockAssignmentRepository.class);
    private final UserBlockExclusionService userBlockExclusionService = mock(UserBlockExclusionService.class);
    private final CourseExclusionRepository courseExclusionRepository = mock(CourseExclusionRepository.class);

    // Die Regeln kommen bewusst aus dem echten Dienst – gemockt würde der Test nur sich selbst prüfen.
    private final CourseEligibilityService eligibilityService = new CourseEligibilityService(
            assignmentRepository, courseBlockAssignmentRepository, userBlockExclusionService, courseExclusionRepository);

    private final AssignableCoursesService service = new AssignableCoursesService(
            userRepository, periodRepository, courseRepository, assignmentRepository,
            courseBlockAssignmentRepository, eligibilityService);

    private final User student = User.builder()
            .userName(USER_NAME)
            .grade(6)
            .gender(Gender.male)
            .build();

    private final Block monday = block(1L, DayOfWeek.MONDAY, "14:00");
    private final Block tuesday = block(2L, DayOfWeek.TUESDAY, "14:00");

    private final List<CourseUserAssignment> currentAssignments = new ArrayList<>();
    private final List<CourseBlockAssignment> blockAssignments = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(userRepository.findById(USER_NAME)).thenReturn(Optional.of(student));
        when(periodRepository.existsById(PERIOD_ID)).thenReturn(true);
        when(assignmentRepository.findByUserAndCourse_Period_Id(student, PERIOD_ID)).thenReturn(currentAssignments);
        when(courseBlockAssignmentRepository.findAllByPeriodId(PERIOD_ID)).thenReturn(blockAssignments);
        when(userBlockExclusionService.getExcludedBlockIds(anyString(), anyLong())).thenReturn(Set.of());
        when(courseExclusionRepository.existsByCourseAndUser(any(), any())).thenReturn(false);
    }

    private static Block block(long id, DayOfWeek day, String start) {
        return Block.builder()
                .id(id)
                .dayOfWeek(day)
                .startTime(LocalTime.parse(start))
                .endTime(LocalTime.parse(start).plusHours(1))
                .build();
    }

    /** Legt einen Kurs der Phase an und hängt ihn in den Block – wie im echten Datenmodell. */
    private Course givenCourse(long id, String name, Block block, int maxAttendees, int grade) {
        Course course = Course.builder()
                .id(id)
                .courseId("K" + id)
                .name(name)
                .maxAttendees(maxAttendees)
                .grades(grade > 0 ? Set.of(grade) : Set.of())
                .courseCategories(Set.of())
                .excludedGenders(Set.of())
                .build();
        blockAssignments.add(CourseBlockAssignment.builder().course(course).block(block).build());
        when(courseBlockAssignmentRepository.findByCourse(course)).thenReturn(Optional.of(CourseBlockAssignment.builder()
                .course(course).block(block).build()));
        when(assignmentRepository.countByCourseAndBlock(course, block)).thenReturn(0);

        List<Course> courses = new ArrayList<>(courseRepository.findAllByPeriod_Id(PERIOD_ID));
        courses.add(course);
        when(courseRepository.findAllByPeriod_Id(PERIOD_ID)).thenReturn(courses);
        return course;
    }

    private void givenAssignment(Course course, Block block) {
        currentAssignments.add(CourseUserAssignment.builder()
                .id((long) currentAssignments.size() + 1)
                .user(student)
                .course(course)
                .block(block)
                .build());
    }

    private List<AssignableCourseDto> result() {
        return service.getAssignableCourses(USER_NAME, PERIOD_ID);
    }

    @Test
    void freierKursIstZuweisbar() {
        givenCourse(1L, "Töpfern", monday, 20, 6);

        AssignableCourseDto dto = result().getFirst();

        assertTrue(dto.assignable());
        assertNull(dto.reason());
        assertNull(dto.warning());
        assertEquals(20, dto.freeSeats());
        assertEquals(0, dto.assignedSeats());
        assertEquals("K1", dto.courseId());
        // Der Name der Konstante, nicht „Montag": Ein DayOfWeek-Feld liefe in den global
        // registrierten DayOfWeekSerializer, das Frontend sortiert und übersetzt aber selbst.
        assertEquals("MONDAY", dto.dayOfWeek());
    }

    @Test
    void falscheKlassenstufeErscheintNicht() {
        givenCourse(1L, "Nur für die Fünfer", monday, 20, 5);

        assertTrue(result().isEmpty());
    }

    @Test
    void kursOhneStufenvorgabeIstFuerAlleDa() {
        givenCourse(1L, "Für alle", monday, 20, 0);

        assertEquals(1, result().size());
    }

    /** Genau der Fall, für den die manuelle Zuweisung existiert – im Wechselwunsch wäre er ausgeblendet. */
    @Test
    void nurManuellVergebenerKursStehtInDerListe() {
        Course course = givenCourse(1L, "Förderkurs", monday, 20, 6);
        course.setManualAssignmentOnly(true);

        AssignableCourseDto dto = result().getFirst();

        assertTrue(dto.assignable());
        assertTrue(dto.manualAssignmentOnly());
    }

    @Test
    void platzhalterKursErscheintNicht() {
        Course course = givenCourse(1L, "Platzhalter", monday, 20, 6);
        course.setPlaceholder(true);

        assertTrue(result().isEmpty());
    }

    @Test
    void bereitsZugewiesenerKursErscheintNicht() {
        Course course = givenCourse(1L, "Töpfern", monday, 20, 6);
        givenAssignment(course, monday);

        assertTrue(result().isEmpty());
    }

    @Test
    void tageskonfliktStehtMitGrundInDerListe() {
        Course assigned = givenCourse(1L, "Töpfern", monday, 20, 6);
        givenAssignment(assigned, monday);
        givenCourse(2L, "Schach", monday, 20, 6);

        AssignableCourseDto dto = result().getFirst();

        assertEquals("Schach", dto.name());
        assertFalse(dto.assignable());
        assertEquals("Tageskonflikt", dto.reason());
    }

    @Test
    void erreichtesKontingentStehtMitGrundInDerListe() {
        for (long i = 1; i <= 3; i++) {
            Course assigned = givenCourse(i, "Kurs " + i, block(10 + i, DayOfWeek.of((int) i), "14:00"), 20, 6);
            givenAssignment(assigned, block(10 + i, DayOfWeek.of((int) i), "14:00"));
        }
        givenCourse(4L, "Vierter Kurs", block(20L, DayOfWeek.FRIDAY, "14:00"), 20, 6);

        AssignableCourseDto dto = result().getFirst();

        assertEquals("Vierter Kurs", dto.name());
        assertFalse(dto.assignable());
        assertTrue(dto.reason().contains("3 Kurse"));
    }

    /** Ob überbucht wird, entscheidet die Verwaltung – der Kurs bleibt wählbar. */
    @Test
    void vollerKursBleibtZuweisbarMitWarnung() {
        Course course = givenCourse(1L, "Töpfern", monday, 10, 6);
        when(assignmentRepository.countByCourseAndBlock(course, monday)).thenReturn(10);

        AssignableCourseDto dto = result().getFirst();

        assertTrue(dto.assignable());
        assertEquals(0, dto.freeSeats());
        assertEquals(10, dto.assignedSeats());
        assertTrue(dto.warning().contains("voll"));
    }

    @Test
    void ueberbuchungBleibtSichtbar() {
        Course course = givenCourse(1L, "Töpfern", monday, 10, 6);
        when(assignmentRepository.countByCourseAndBlock(course, monday)).thenReturn(12);

        AssignableCourseDto dto = result().getFirst();

        assertEquals(0, dto.freeSeats());
        assertEquals(12, dto.assignedSeats());
        assertEquals(10, dto.capacity());
    }

    @Test
    void persoenlicherKursausschlussBlendetKursAus() {
        Course course = givenCourse(1L, "Töpfern", monday, 20, 6);
        when(courseExclusionRepository.existsByCourseAndUser(course, student)).thenReturn(true);

        assertTrue(result().isEmpty());
    }

    @Test
    void gesperrterBlockBlendetKursAus() {
        givenCourse(1L, "Töpfern", monday, 20, 6);
        when(userBlockExclusionService.getExcludedBlockIds(USER_NAME, PERIOD_ID)).thenReturn(Set.of(monday.getId()));

        assertTrue(result().isEmpty());
    }

    @Test
    void ausgeschlossenesGeschlechtBlendetKursAus() {
        Course course = givenCourse(1L, "Mädchenkurs", monday, 20, 6);
        course.setExcludedGenders(Set.of(Gender.male));

        assertTrue(result().isEmpty());
    }

    @Test
    void kursOhneBlockErscheintNicht() {
        Course course = Course.builder()
                .id(9L).courseId("K9").name("Noch nicht eingeplant").maxAttendees(20)
                .grades(Set.of(6)).courseCategories(Set.of()).excludedGenders(Set.of())
                .build();
        when(courseRepository.findAllByPeriod_Id(PERIOD_ID)).thenReturn(List.of(course));

        assertTrue(result().isEmpty());
    }

    @Test
    void sortiertNachBlock() {
        givenCourse(1L, "Schach", tuesday, 20, 6);
        givenCourse(2L, "Töpfern", monday, 20, 6);

        assertEquals(List.of("Töpfern", "Schach"), result().stream().map(AssignableCourseDto::name).toList());
    }

    @Test
    void unbekannterSchuelerMeldetUserNotFound() {
        when(userRepository.findById("gibtsnicht")).thenReturn(Optional.empty());

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.getAssignableCourses("gibtsnicht", PERIOD_ID));
        assertEquals(ErrorCode.UserNotFound, ex.getErrorCode());
    }

    @Test
    void unbekanntePhaseMeldetNotFound() {
        when(periodRepository.existsById(99L)).thenReturn(false);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.getAssignableCourses(USER_NAME, 99L));
        assertEquals(ErrorCode.NotFound, ex.getErrorCode());
    }
}
