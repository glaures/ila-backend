package sandbox27.ila.backend.preference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sandbox27.ila.backend.assignments.CourseUserAssignmentRepository;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseBlockAssignment;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.course.CourseCategory;
import sandbox27.ila.backend.course.CourseService;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.user.User;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Die Vorgabe, drei verschiedene Kategorien auf Platz 1 zu setzen, gilt nur bis Klasse 10.
 */
class CategoryRuleTest {

    private static final long PERIOD_ID = 1L;

    private final PeriodRepository periodRepository = mock(PeriodRepository.class);
    private final BlockRepository blockRepository = mock(BlockRepository.class);
    private final PreferenceRepository preferenceRepository = mock(PreferenceRepository.class);
    private final CourseUserAssignmentRepository courseUserAssignmentRepository = mock(CourseUserAssignmentRepository.class);
    private final CourseBlockAssignmentRepository courseBlockAssignmentRepository = mock(CourseBlockAssignmentRepository.class);
    private final PeriodUserPreferencesSubmitStatusRepository submitStatusRepository = mock(PeriodUserPreferencesSubmitStatusRepository.class);
    private final CourseService courseService = mock(CourseService.class);

    private final PreferencesStatusService service = new PreferencesStatusService(
            periodRepository, preferenceRepository, blockRepository, courseUserAssignmentRepository,
            courseBlockAssignmentRepository, submitStatusRepository, courseService);

    private final Period period = Period.builder().id(PERIOD_ID).name("Testphase").current(true).build();
    private final List<Block> blocks = new ArrayList<>();

    @BeforeEach
    void setUp() {
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY)) {
            blocks.add(Block.builder().id(blocks.size() + 1).period(period)
                    .dayOfWeek(day).startTime(LocalTime.of(14, 0)).endTime(LocalTime.of(15, 0)).build());
        }
        when(periodRepository.findByCurrent(true)).thenReturn(Optional.of(period));
        when(blockRepository.findAllByPeriod_idOrderByDayOfWeekAscStartTimeAsc(PERIOD_ID)).thenReturn(blocks);
        when(courseUserAssignmentRepository.findByUserAndBlock_Period(any(), any())).thenReturn(List.of());
        when(courseService.getSelectableCourses(anyLong(), any())).thenReturn(List.of(new Course()));
        when(submitStatusRepository.findByUserAndPeriod(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void tenthGraderNeedsThreeDifferentCategories() {
        User user = student(10);
        givenFirstPreferencesWithCategories(user, CourseCategory.iLa, CourseCategory.iLa, CourseCategory.iLa);

        PreferencesStatusService.PreferencesStatus status = service.getPreferencesStatus(user);
        assertFalse(status.isCategoryDistributionOk());
        assertFalse(status.readyToSubmit());
        assertTrue(status.advices().stream().anyMatch(a -> a.contains("Kategorien")));
    }

    @Test
    void eleventhGraderMaySubmitWithASingleCategory() {
        User user = student(11);
        givenFirstPreferencesWithCategories(user, CourseCategory.iLa, CourseCategory.iLa, CourseCategory.iLa);

        PreferencesStatusService.PreferencesStatus status = service.getPreferencesStatus(user);
        assertTrue(status.isCategoryDistributionOk());
        assertTrue(status.readyToSubmit());
        assertTrue(status.advices().isEmpty());
    }

    @Test
    void theRuleStillAppliesUpToGradeTen() {
        assertTrue(service.isCategoryRuleApplicable(student(5)));
        assertTrue(service.isCategoryRuleApplicable(student(10)));
        assertFalse(service.isCategoryRuleApplicable(student(11)));
        assertFalse(service.isCategoryRuleApplicable(student(12)));
    }

    /**
     * Der Oberstufenschüler muss weiterhin alle Blöcke bearbeiten – nur die Kategorienvorgabe
     * entfällt.
     */
    @Test
    void eleventhGraderStillHasToEditAllBlocks() {
        User user = student(11);
        givenFirstPreferencesWithCategories(user, CourseCategory.iLa);

        PreferencesStatusService.PreferencesStatus status = service.getPreferencesStatus(user);
        assertTrue(status.isCategoryDistributionOk());
        assertFalse(status.readyToSubmit());
        assertTrue(status.advices().stream().anyMatch(a -> a.contains("Blöcke")));
    }

    private User student(int grade) {
        return User.builder().userName("schueler" + grade).grade(grade).build();
    }

    /** Setzt je Kategorie eine Erstpräferenz in einem eigenen Block. */
    private void givenFirstPreferencesWithCategories(User user, CourseCategory... categories) {
        List<Preference> preferences = new ArrayList<>();
        for (int i = 0; i < categories.length; i++) {
            Block block = blocks.get(i);
            Course course = new Course();
            course.setId(i + 1);
            course.setCourseCategories(Set.of(categories[i]));
            preferences.add(Preference.builder().user(user).block(block).course(course).preferenceIndex(0).build());
            when(courseBlockAssignmentRepository.findByCourse(course))
                    .thenReturn(Optional.of(CourseBlockAssignment.builder().course(course).block(block).build()));
        }
        when(preferenceRepository.findByUserAndBlock_Period(user, period)).thenReturn(preferences);
    }
}
