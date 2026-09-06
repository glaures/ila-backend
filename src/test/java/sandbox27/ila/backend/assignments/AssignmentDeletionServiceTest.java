package sandbox27.ila.backend.assignments;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import sandbox27.ila.backend.assignments.events.CourseAssignmentDeleteEvent;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.user.User;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Beim Löschen von Zuweisungen müssen die daran hängenden Wechselwünsche mit verschwinden –
 * sonst scheitert das Löschen am Fremdschlüssel {@code exchange_request.current_assignment_id}.
 */
class AssignmentDeletionServiceTest {

    private static final long PERIOD_ID = 1L;

    private final CourseUserAssignmentRepository courseUserAssignmentRepository = mock(CourseUserAssignmentRepository.class);
    private final ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);

    private final AssignmentDeletionService service =
            new AssignmentDeletionService(courseUserAssignmentRepository, applicationEventPublisher);

    private final Period period = Period.builder().id(PERIOD_ID).name("Testphase").build();

    private final CourseUserAssignment algorithmic1 = assignment(1L, "anna", false);
    private final CourseUserAssignment algorithmic2 = assignment(2L, "ben", false);
    private final CourseUserAssignment manual = assignment(3L, "clara", true);

    @Test
    void announcesEveryDeletionBeforeRemovingTheAssignments() {
        int deleted = service.delete(List.of(algorithmic1, algorithmic2));

        assertEquals(2, deleted);
        verify(applicationEventPublisher).publishEvent(new CourseAssignmentDeleteEvent(1L));
        verify(applicationEventPublisher).publishEvent(new CourseAssignmentDeleteEvent(2L));
        verify(applicationEventPublisher, times(2)).publishEvent(any(CourseAssignmentDeleteEvent.class));

        // Reihenfolge zählt: erst die Wechselwünsche abräumen, dann die Zuweisungen löschen
        InOrder inOrder = inOrder(applicationEventPublisher, courseUserAssignmentRepository);
        inOrder.verify(applicationEventPublisher, times(2)).publishEvent(any(CourseAssignmentDeleteEvent.class));
        inOrder.verify(courseUserAssignmentRepository).deleteAll(any());
    }

    @Test
    void doesNothingForAnEmptyList() {
        assertEquals(0, service.delete(List.of()));

        verify(courseUserAssignmentRepository, never()).deleteAll(any());
        verify(applicationEventPublisher, never()).publishEvent(any(CourseAssignmentDeleteEvent.class));
    }

    @Test
    void deletesOnlyAlgorithmicAssignmentsOfThePeriod() {
        when(courseUserAssignmentRepository.findByCourse_Period(period))
                .thenReturn(List.of(algorithmic1, manual, algorithmic2));

        assertEquals(2, service.deleteAlgorithmicAssignments(period));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CourseUserAssignment>> captor = ArgumentCaptor.forClass(List.class);
        verify(courseUserAssignmentRepository).deleteAll(captor.capture());
        assertEquals(List.of(algorithmic1, algorithmic2), captor.getValue());
        assertTrue(captor.getValue().stream().noneMatch(CourseUserAssignment::isPreset),
                "Manuelle Zuweisungen dürfen nicht gelöscht werden");
    }

    @Test
    void keepsEverythingWhenOnlyManualAssignmentsExist() {
        when(courseUserAssignmentRepository.findByCourse_Period(period)).thenReturn(List.of(manual));

        assertEquals(0, service.deleteAlgorithmicAssignments(period));
        verify(courseUserAssignmentRepository, never()).deleteAll(any());
    }

    static CourseUserAssignment assignment(long id, String userName, boolean preset) {
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
