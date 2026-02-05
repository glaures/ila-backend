package sandbox27.ila.backend.assignments;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import sandbox27.ila.backend.assignments.algorithm.AssignmentResult;
import sandbox27.ila.backend.assignments.algorithm.AssignmentResultRepository;
import sandbox27.ila.backend.assignments.algorithm.CourseAssignmentService;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AssignmentResultService {

    final ApplicationEventPublisher applicationEventPublisher;
    final AssignmentResultRepository assignmentResultRepository;
    final PeriodRepository periodRepository;
    final CourseUserAssignmentRepository courseUserAssignmentRepository;

    @Transactional
    public List<AssignmentResult> markCourseAssignmentProcessFinal(@PathVariable long assignmentResultId) throws ServiceException {
        AssignmentResult assignmentResult = assignmentResultRepository.findById(assignmentResultId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));
        assignmentResult.setFinalized(true);
        assignmentResult = assignmentResultRepository.save(assignmentResult);
        AssignmentsFinalEvent assignmentsFinalEvent = new AssignmentsFinalEvent(assignmentResult.getPeriod().getId());
        applicationEventPublisher.publishEvent(assignmentsFinalEvent);
        return assignmentResultRepository.findByPeriod_IdOrderByExecutedAtDesc(assignmentResult.getPeriod().getId());
    }

    @Transactional
    public boolean isCurrentPeriodFinalized() {
        Period currentPeriod = periodRepository.findByCurrent(true)
                .orElse(null);
        if (currentPeriod == null) return false;
        return assignmentResultRepository.existsByPeriodAndFinalizedTrue(currentPeriod);
    }

    @Transactional
    public void deleteAssignmentResult(Long assignmentResultId) {
        AssignmentResult assignmentResult = assignmentResultRepository.findById(assignmentResultId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));

        Period period = assignmentResult.getPeriod();

        // Prüfen, ob dies der zeitlich letzte Durchlauf der Periode ist
        boolean isLatest = !assignmentResultRepository
                .existsByPeriodAndExecutedAtAfter(period, assignmentResult.getExecutedAt());

        assignmentResultRepository.delete(assignmentResult);

        // Nur beim letzten Durchlauf auch die algorithmischen Zuweisungen löschen
        if (isLatest) {
            courseUserAssignmentRepository.deleteByPresetFalseAndBlock_Period(period);
        }
    }
}
