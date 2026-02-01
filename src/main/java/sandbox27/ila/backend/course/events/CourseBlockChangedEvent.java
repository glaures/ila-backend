package sandbox27.ila.backend.course.events;

public record CourseBlockChangedEvent(Long courseId, Long newBlockId) {
}
