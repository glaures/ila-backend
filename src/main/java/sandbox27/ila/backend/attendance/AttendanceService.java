package sandbox27.ila.backend.attendance;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sandbox27.ila.backend.assignments.CourseUserAssignment;
import sandbox27.ila.backend.assignments.CourseUserAssignmentRepository;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceEntryRepository entryRepository;
    private final CourseRepository courseRepository;
    private final CourseUserAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;

    public List<AttendanceSessionDto> getSessionsForCourse(Long courseId) {
        return sessionRepository.findByCourseIdOrderByDateDesc(courseId)
                .stream()
                .map(AttendanceSessionDto::fromEntity)
                .collect(Collectors.toList());
    }

    public AttendanceSessionDto getSession(Long sessionId) {
        AttendanceSession session = sessionRepository.findByIdWithEntries(sessionId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));
        return AttendanceSessionDto.fromEntity(session);
    }

    public List<AttendanceEntryDto> getEntriesForSession(Long sessionId) {
        AttendanceSession session = sessionRepository.findByIdWithEntries(sessionId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));

        return session.getEntries().stream()
                .map(AttendanceEntryDto::fromEntity)
                .sorted((a, b) -> {
                    int firstNameCompare = a.firstName().compareToIgnoreCase(b.firstName());
                    if (firstNameCompare != 0) return firstNameCompare;
                    return a.lastName().compareToIgnoreCase(b.lastName());
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public AttendanceSessionDto createSession(CreateAttendanceSessionRequest request) {
        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));

        // Prüfen ob bereits ein Termin für dieses Datum existiert
        if (sessionRepository.findByCourseIdAndDate(request.courseId(), request.date()).isPresent()) {
            throw new ServiceException(ErrorCode.AttendanceSessionAlreadyExists);
        }

        AttendanceSession session = AttendanceSession.builder()
                .course(course)
                .date(request.date())
                .notes(request.notes())
                .build();

        session = sessionRepository.save(session);

        // Alle aktuellen Kursteilnehmer als "anwesend" eintragen
        List<CourseUserAssignment> assignments = assignmentRepository.findByCourse_idOrderByUser_LastName(course.getId());
        for (CourseUserAssignment assignment : assignments) {
            AttendanceEntry entry = AttendanceEntry.builder()
                    .session(session)
                    .user(assignment.getUser())
                    .present(true)
                    .build();
            entry = entryRepository.save(entry);
            session.getEntries().add(entry);
        }

        return AttendanceSessionDto.fromEntity(session);
    }

    @Transactional
    public AttendanceSessionDto updateSession(Long sessionId, CreateAttendanceSessionRequest request) {
        AttendanceSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));

        // Prüfen ob das neue Datum bereits vergeben ist (außer es ist der gleiche Termin)
        sessionRepository.findByCourseIdAndDate(session.getCourse().getId(), request.date())
                .ifPresent(existing -> {
                    if (!existing.getId().equals(sessionId)) {
                        throw new ServiceException(ErrorCode.AttendanceSessionAlreadyExists);
                    }
                });

        session.setDate(request.date());
        session.setNotes(request.notes());
        session = sessionRepository.save(session);

        return AttendanceSessionDto.fromEntity(session);
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        AttendanceSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));
        sessionRepository.delete(session);
    }

    @Transactional
    public List<AttendanceEntryDto> updateEntries(Long sessionId, List<UpdateAttendanceEntryRequest> requests) {
        AttendanceSession session = sessionRepository.findByIdWithEntries(sessionId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));

        // Bestehende Einträge als Map für schnellen Zugriff
        Map<String, AttendanceEntry> existingEntries = session.getEntries().stream()
                .collect(Collectors.toMap(e -> e.getUser().getUserName(), Function.identity()));

        for (UpdateAttendanceEntryRequest request : requests) {
            AttendanceEntry entry = existingEntries.get(request.userName());
            if (entry != null) {
                entry.setPresent(request.present());
                entry.setNote(request.note());
                entryRepository.save(entry);
            } else {
                // Neuer Eintrag - User muss existieren
                User user = userRepository.findById(request.userName())
                        .orElseThrow(() -> new ServiceException(ErrorCode.UserNotFound));

                AttendanceEntry newEntry = AttendanceEntry.builder()
                        .session(session)
                        .user(user)
                        .present(request.present())
                        .note(request.note())
                        .build();
                newEntry = entryRepository.save(newEntry);
                session.getEntries().add(newEntry);
            }
        }

        return getEntriesForSession(sessionId);
    }

    /**
     * Prüft ob der Benutzer Zugriff auf den Kurs hat (als Admin oder als Kursleiter)
     */
    public boolean hasAccessToCourse(Long courseId, User user) {
        if (user.hasRole("ADMIN")) {
            return true;
        }

        Course course = courseRepository.findById(courseId).orElse(null);
        if (course == null) {
            return false;
        }

        return course.getInstructor() != null &&
                course.getInstructor().getUserName().equals(user.getUserName());
    }

    /**
     * Prüft ob der Benutzer Zugriff auf die Session hat
     */
    public boolean hasAccessToSession(Long sessionId, User user) {
        AttendanceSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return false;
        }
        return hasAccessToCourse(session.getCourse().getId(), user);
    }
}