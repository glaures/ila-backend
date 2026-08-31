package sandbox27.ila.backend.preference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sandbox27.ila.backend.assignments.CourseUserAssignment;
import sandbox27.ila.backend.assignments.CourseUserAssignmentRepository;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseBlockAssignment;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.course.CourseService;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.user.User;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Deckt ab, dass Blöcke ohne für den Schüler wählbare Kurse die Abgabe nicht blockieren.
 */
class BlockProgressTest {

    private static final long PERIOD_ID = 1L;

    private final BlockRepository blockRepository = mock(BlockRepository.class);
    private final PreferenceRepository preferenceRepository = mock(PreferenceRepository.class);
    private final CourseUserAssignmentRepository courseUserAssignmentRepository = mock(CourseUserAssignmentRepository.class);
    private final CourseBlockAssignmentRepository courseBlockAssignmentRepository = mock(CourseBlockAssignmentRepository.class);
    private final CourseService courseService = mock(CourseService.class);

    private final PreferencesStatusService service = new PreferencesStatusService(
            mock(PeriodRepository.class),
            preferenceRepository,
            blockRepository,
            courseUserAssignmentRepository,
            courseBlockAssignmentRepository,
            mock(PeriodUserPreferencesSubmitStatusRepository.class),
            courseService);

    private final Period period = Period.builder().id(PERIOD_ID).name("Testphase").build();
    private final User user = User.builder().userName("max.mustermann").grade(6).build();
    private final List<Block> blocks = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // 10 Blöcke: je zwei an fünf Wochentagen, IDs 1..10
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            blocks.add(block(blocks.size() + 1, day, LocalTime.of(14, 0)));
            blocks.add(block(blocks.size() + 1, day, LocalTime.of(15, 45)));
        }
        when(blockRepository.findAllByPeriod_idOrderByDayOfWeekAscStartTimeAsc(PERIOD_ID)).thenReturn(blocks);
        when(courseUserAssignmentRepository.findByUserAndBlock_Period(user, period)).thenReturn(List.of());
        when(preferenceRepository.findByUserAndBlock_Period(user, period)).thenReturn(List.of());
        // Ausgangslage: in jedem Block ist etwas wählbar
        when(courseService.getSelectableCourses(anyLong(), eq(user))).thenReturn(List.of(new Course()));
    }

    @Test
    void allBlocksCountWhenCoursesAreAvailableEverywhere() {
        givenPreferencesInBlocks(1, 2, 3, 4, 5, 6, 7, 8);

        assertEquals(10, relevantBlocks());
        assertEquals(0.8, progress(), 0.0001);
    }

    @Test
    void blocksWithoutSelectableCoursesDoNotBlockSubmission() {
        // In zwei Blöcken gibt es für diesen Schüler nichts zu wählen
        when(courseService.getSelectableCourses(eq(9L), eq(user))).thenReturn(List.of());
        when(courseService.getSelectableCourses(eq(10L), eq(user))).thenReturn(List.of());
        givenPreferencesInBlocks(1, 2, 3, 4, 5, 6, 7, 8);

        assertEquals(8, relevantBlocks());
        assertEquals(1.0, progress(), 0.0001);
    }

    @Test
    void progressIsCompleteWhenNothingIsSelectableAtAll() {
        when(courseService.getSelectableCourses(anyLong(), eq(user))).thenReturn(List.of());

        assertEquals(0, relevantBlocks());
        assertEquals(1.0, progress(), 0.0001);
    }

    @Test
    void alreadyEditedBlocksStayRelevantWhenTheirCoursesVanish() {
        // Kursänderung nach der Bearbeitung: in Block 1 ist nichts mehr wählbar. Die dort
        // gesetzte Präferenz darf den Fortschritt nicht über 100% treiben.
        when(courseService.getSelectableCourses(eq(1L), eq(user))).thenReturn(List.of());
        givenPreferencesInBlocks(1, 2, 3);

        assertEquals(10, relevantBlocks());
        assertEquals(0.3, progress(), 0.0001);
    }

    @Test
    void aFixedAssignmentCoversBothBlocksOfThatDay() {
        CourseUserAssignment assignment = CourseUserAssignment.builder()
                .user(user).block(blocks.get(0)).build();
        when(courseUserAssignmentRepository.findByUserAndBlock_Period(user, period)).thenReturn(List.of(assignment));
        when(blockRepository.findByPeriod_IdAndDayOfWeek(PERIOD_ID, DayOfWeek.MONDAY))
                .thenReturn(List.of(blocks.get(0), blocks.get(1)));

        assertEquals(10, relevantBlocks());
        assertEquals(0.2, progress(), 0.0001);
    }

    private void givenPreferencesInBlocks(long... blockIds) {
        List<Preference> preferences = new ArrayList<>();
        for (long blockId : blockIds) {
            Block block = blocks.stream().filter(b -> b.getId() == blockId).findFirst().orElseThrow();
            Course course = new Course();
            course.setId(blockId * 100);
            preferences.add(Preference.builder().user(user).block(block).course(course).preferenceIndex(0).build());
            when(courseBlockAssignmentRepository.findByCourse(course))
                    .thenReturn(Optional.of(CourseBlockAssignment.builder().course(course).block(block).build()));
        }
        when(preferenceRepository.findByUserAndBlock_Period(user, period)).thenReturn(preferences);
    }

    private int relevantBlocks() {
        return service.getBlockProgress(period, user).relevantBlockIds().size();
    }

    private double progress() {
        return service.getBlockProgress(period, user).progress();
    }

    private static Block block(long id, DayOfWeek day, LocalTime start) {
        return Block.builder()
                .id(id)
                .period(Period.builder().id(PERIOD_ID).build())
                .dayOfWeek(day)
                .startTime(start)
                .endTime(start.plusHours(1))
                .build();
    }
}
