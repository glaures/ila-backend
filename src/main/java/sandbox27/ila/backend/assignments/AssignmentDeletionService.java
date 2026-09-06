package sandbox27.ila.backend.assignments;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sandbox27.ila.backend.assignments.events.CourseAssignmentDeleteEvent;
import sandbox27.ila.backend.period.Period;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Einziger Weg, Kurszuweisungen in größerer Zahl zu löschen.
 * <p>
 * Grund für die eigene Klasse: An einer Zuweisung können Wechselwünsche hängen
 * ({@code exchange_request.current_assignment_id} ist der einzige Fremdschlüssel auf
 * {@code course_user_assignment}). Werden sie nicht vorher abgeräumt, scheitert das Löschen an
 * der Constraint. Das Abräumen erledigt der Listener zu {@link CourseAssignmentDeleteEvent} im
 * Wechselwunsch-Modul – das Event muss aber auch tatsächlich gefeuert werden, und genau das ging
 * bei den Massenlöschungen unter (Neulauf des Algorithmus, Verwerfen eines Laufs).
 * <p>
 * Deshalb führt hier ein Weg über {@link #delete(Collection)}; abgeleitete Bulk-Deletes am
 * Repository umgehen den Lebenszyklus und dürfen für Zuweisungen nicht mehr verwendet werden.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssignmentDeletionService {

    private final CourseUserAssignmentRepository courseUserAssignmentRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Löscht die übergebenen Zuweisungen und die Wechselwünsche, die auf sie zeigen.
     *
     * @return Zahl der gelöschten Zuweisungen
     */
    @Transactional
    public int delete(Collection<CourseUserAssignment> assignments) {
        if (assignments.isEmpty())
            return 0;
        // Erst die abhängigen Wechselwünsche, dann die Zuweisungen selbst
        assignments.forEach(a -> applicationEventPublisher.publishEvent(new CourseAssignmentDeleteEvent(a.getId())));
        courseUserAssignmentRepository.deleteAll(assignments);
        return assignments.size();
    }

    /**
     * Die vom Algorithmus erzeugten Zuweisungen einer Phase – also alles außer den von Hand
     * gesetzten ({@code preset = true}).
     */
    @Transactional
    public List<CourseUserAssignment> findAlgorithmicAssignments(Period period) {
        return courseUserAssignmentRepository.findByCourse_Period(period).stream()
                .filter(a -> !a.isPreset())
                .collect(Collectors.toList());
    }

    /**
     * Verwirft die vom Algorithmus erzeugten Zuweisungen einer Phase. Handzuweisungen bleiben.
     *
     * @return Zahl der gelöschten Zuweisungen
     */
    @Transactional
    public int deleteAlgorithmicAssignments(Period period) {
        int deleted = delete(findAlgorithmicAssignments(period));
        log.info("{} algorithmische Zuweisungen in Phase {} gelöscht", deleted, period.getName());
        return deleted;
    }
}
