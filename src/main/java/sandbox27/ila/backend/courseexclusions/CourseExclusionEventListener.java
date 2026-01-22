package sandbox27.ila.backend.courseexclusions;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import sandbox27.ila.backend.course.events.CourseDeletedEvent;

@Component
@RequiredArgsConstructor
@Log4j2
public class CourseExclusionEventListener {

    private final CourseExclusionRepository courseExclusionRepository;

    /**
     * Löscht alle Kursausschlüsse, wenn ein Kurs gelöscht wird.
     * Läuft vor dem Commit der Transaktion, damit bei Fehlern
     * alles zurückgerollt wird und keine Datenleichen entstehen.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onCourseDeleted(CourseDeletedEvent event) {
        log.info("Lösche Kursausschlüsse für Kurs {}", event.courseId());
        courseExclusionRepository.deleteAllByCourseId(event.courseId());
    }
}
