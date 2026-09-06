package sandbox27.ila.backend.assignments.algorithm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sandbox27.ila.backend.assignments.AssignmentDeletionService;
import sandbox27.ila.backend.assignments.CourseUserAssignmentRepository;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.exclusion.UserBlockExclusionService;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.preference.PreferenceRepository;
import sandbox27.ila.backend.user.UserRepository;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Die Schutzregeln um das Verwerfen eines Vergabelaufs. Das Löschen selbst – und dass dabei die
 * Wechselwünsche mitgehen – prüft {@code AssignmentDeletionServiceTest}.
 */
class DeleteAlgorithmicAssignmentsTest {

    private static final long PERIOD_ID = 1L;

    private final PeriodRepository periodRepository = mock(PeriodRepository.class);
    private final AssignmentResultRepository assignmentResultRepository = mock(AssignmentResultRepository.class);
    private final AssignmentDeletionService assignmentDeletionService = mock(AssignmentDeletionService.class);

    private final CourseAssignmentService service = new CourseAssignmentService(
            periodRepository,
            mock(UserRepository.class),
            mock(CourseRepository.class),
            mock(BlockRepository.class),
            mock(PreferenceRepository.class),
            mock(CourseUserAssignmentRepository.class),
            mock(CourseBlockAssignmentRepository.class),
            mock(UserBlockExclusionService.class),
            assignmentResultRepository,
            assignmentDeletionService);

    private final Period period = Period.builder().id(PERIOD_ID).name("Testphase").build();

    @BeforeEach
    void setUp() {
        when(periodRepository.findById(PERIOD_ID)).thenReturn(Optional.of(period));
        when(assignmentResultRepository.existsByPeriodAndFinalizedTrue(period)).thenReturn(false);
        when(assignmentDeletionService.deleteAlgorithmicAssignments(period)).thenReturn(7);
    }

    @Test
    void discardsTheAlgorithmicAssignmentsOfThePeriod() {
        assertEquals(7, service.deleteAlgorithmicAssignments(PERIOD_ID));

        verify(assignmentDeletionService).deleteAlgorithmicAssignments(period);
    }

    @Test
    void refusesWhenTheAssignmentsAreAlreadyFinal() {
        when(assignmentResultRepository.existsByPeriodAndFinalizedTrue(period)).thenReturn(true);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.deleteAlgorithmicAssignments(PERIOD_ID));

        assertEquals(ErrorCode.AssignmentsAlreadyFinalized, exception.getErrorCode());
        verify(assignmentDeletionService, never()).deleteAlgorithmicAssignments(any());
    }

    @Test
    void failsForAnUnknownPeriod() {
        when(periodRepository.findById(99L)).thenReturn(Optional.empty());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.deleteAlgorithmicAssignments(99L));

        assertEquals(ErrorCode.NotFound, exception.getErrorCode());
        verify(assignmentDeletionService, never()).deleteAlgorithmicAssignments(any());
    }
}
