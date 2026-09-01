package sandbox27.ila.backend.assignments.algorithm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import sandbox27.ila.backend.assignments.CourseUserAssignment;
import sandbox27.ila.backend.assignments.CourseUserAssignmentRepository;
import sandbox27.ila.backend.assignments.events.CourseAssignmentDeleteEvent;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.exclusion.UserBlockExclusionService;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.preference.PreferenceRepository;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Das Verwerfen eines Vergabelaufs darf ausschließlich die automatisch erzeugten Zuweisungen
 * treffen – manuell gesetzte ({@code preset}) müssen stehen bleiben.
 */
class DeleteAlgorithmicAssignmentsTest {

    private static final long PERIOD_ID = 1L;

    private final PeriodRepository periodRepository = mock(PeriodRepository.class);
    private final CourseUserAssignmentRepository courseUserAssignmentRepository = mock(CourseUserAssignmentRepository.class);
    private final AssignmentResultRepository assignmentResultRepository = mock(AssignmentResultRepository.class);
    private final ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);

    private final CourseAssignmentService service = new CourseAssignmentService(
            periodRepository,
            mock(UserRepository.class),
            mock(CourseRepository.class),
            mock(BlockRepository.class),
            mock(PreferenceRepository.class),
            courseUserAssignmentRepository,
            mock(CourseBlockAssignmentRepository.class),
            mock(UserBlockExclusionService.class),
            assignmentResultRepository,
            applicationEventPublisher);

    private final Period period = Period.builder().id(PERIOD_ID).name("Testphase").build();

    private final CourseUserAssignment algorithmic1 = assignment(1L, "anna", false);
    private final CourseUserAssignment algorithmic2 = assignment(2L, "ben", false);
    private final CourseUserAssignment manual = assignment(3L, "clara", true);

    @BeforeEach
    void setUp() {
        when(periodRepository.findById(PERIOD_ID)).thenReturn(Optional.of(period));
        when(assignmentResultRepository.existsByPeriodAndFinalizedTrue(period)).thenReturn(false);
        when(courseUserAssignmentRepository.findByCourse_Period(period))
                .thenReturn(List.of(algorithmic1, manual, algorithmic2));
    }

    @Test
    void deletesOnlyAlgorithmicAssignments() {
        int deleted = service.deleteAlgorithmicAssignments(PERIOD_ID);

        assertEquals(2, deleted);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CourseUserAssignment>> captor = ArgumentCaptor.forClass(List.class);
        verify(courseUserAssignmentRepository).deleteAll(captor.capture());
        assertEquals(List.of(algorithmic1, algorithmic2), captor.getValue());
        assertTrue(captor.getValue().stream().noneMatch(CourseUserAssignment::isPreset),
                "Manuelle Zuweisungen dürfen nicht gelöscht werden");
    }

    @Test
    void announcesEveryDeletionSoExchangeRequestsAreCleanedUp() {
        service.deleteAlgorithmicAssignments(PERIOD_ID);

        // Ohne das Event bliebe der Fremdschlüssel exchange_request.current_assignment_id stehen
        verify(applicationEventPublisher).publishEvent(new CourseAssignmentDeleteEvent(1L));
        verify(applicationEventPublisher).publishEvent(new CourseAssignmentDeleteEvent(2L));
        verify(applicationEventPublisher, times(2)).publishEvent(any(CourseAssignmentDeleteEvent.class));
    }

    @Test
    void refusesWhenTheAssignmentsAreAlreadyFinal() {
        when(assignmentResultRepository.existsByPeriodAndFinalizedTrue(period)).thenReturn(true);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.deleteAlgorithmicAssignments(PERIOD_ID));
        assertEquals(ErrorCode.AssignmentsAlreadyFinalized, exception.getErrorCode());
        verify(courseUserAssignmentRepository, never()).deleteAll(any());
    }

    @Test
    void doesNothingWhenOnlyManualAssignmentsExist() {
        when(courseUserAssignmentRepository.findByCourse_Period(period)).thenReturn(List.of(manual));

        assertEquals(0, service.deleteAlgorithmicAssignments(PERIOD_ID));
        verify(courseUserAssignmentRepository, never()).deleteAll(any());
        verify(applicationEventPublisher, never()).publishEvent(any(CourseAssignmentDeleteEvent.class));
    }

    @Test
    void failsForAnUnknownPeriod() {
        when(periodRepository.findById(99L)).thenReturn(Optional.empty());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.deleteAlgorithmicAssignments(99L));
        assertEquals(ErrorCode.NotFound, exception.getErrorCode());
    }

    private static CourseUserAssignment assignment(long id, String userName, boolean preset) {
        Block block = Block.builder().id(id).period(Period.builder().id(PERIOD_ID).build())
                .dayOfWeek(DayOfWeek.MONDAY).startTime(LocalTime.of(14, 0)).endTime(LocalTime.of(15, 0)).build();
        Course course = new Course();
        course.setId(id);
        return CourseUserAssignment.builder()
                .id(id)
                .user(User.builder().userName(userName).grade(7).build())
                .course(course)
                .block(block)
                .preset(preset)
                .build();
    }
}
