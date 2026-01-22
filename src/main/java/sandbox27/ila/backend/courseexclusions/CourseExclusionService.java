package sandbox27.ila.backend.courseexclusions;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.user.User;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service für Kursausschluss-Logik, die von anderen Services verwendet werden kann.
 */
@Service
@RequiredArgsConstructor
public class CourseExclusionService {

    private final CourseExclusionRepository courseExclusionRepository;

    /**
     * Prüft, ob ein Benutzer von einem Kurs ausgeschlossen ist
     */
    public boolean isUserExcludedFromCourse(Course course, User user) {
        return courseExclusionRepository.existsByCourseAndUser(course, user);
    }

    /**
     * Prüft, ob ein Benutzer von einem Kurs ausgeschlossen ist (per IDs)
     */
    public boolean isUserExcludedFromCourse(Long courseId, String userName) {
        return courseExclusionRepository.findByCourseIdAndUserName(courseId, userName).isPresent();
    }

    /**
     * Gibt alle ausgeschlossenen Benutzer-IDs für einen Kurs zurück
     */
    public Set<String> getExcludedUserNames(Long courseId) {
        return courseExclusionRepository.findAllByCourseId(courseId).stream()
                .map(e -> e.getUser().getUserName())
                .collect(Collectors.toSet());
    }

    /**
     * Gibt alle Kurs-IDs zurück, von denen ein Benutzer ausgeschlossen ist
     */
    public Set<Long> getExcludedCourseIds(String userName) {
        return courseExclusionRepository.findAllByUser(
                User.builder().userName(userName).build()
        ).stream()
                .map(e -> e.getCourse().getId())
                .collect(Collectors.toSet());
    }

    /**
     * Gibt alle Ausschlüsse für einen Kurs zurück
     */
    public List<CourseExclusion> getExclusionsForCourse(Course course) {
        return courseExclusionRepository.findAllByCourse(course);
    }

    /**
     * Löscht alle Ausschlüsse für einen Kurs (z.B. wenn der Kurs gelöscht wird)
     */
    public void deleteAllExclusionsForCourse(Course course) {
        courseExclusionRepository.deleteByCourse(course);
    }
}
