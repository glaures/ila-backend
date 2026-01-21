package sandbox27.ila.backend.attendance;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.user.Role;
import sandbox27.ila.backend.user.User;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;
import sandbox27.infrastructure.security.AuthenticatedUser;
import sandbox27.infrastructure.security.RequiredRole;

import java.util.List;

@RestController
@RequestMapping("/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    /**
     * Gibt alle Termine für einen Kurs zurück
     */
    @GetMapping("/sessions")
    @RequiredRole({Role.ADMIN_ROLE_NAME, Role.COURSE_INSTRUCTOR_ROLE_NAME})
    public ResponseEntity<List<AttendanceSessionDto>> getSessionsForCourse(
            @RequestParam("course-id") Long courseId,
            @AuthenticatedUser User currentUser) {

        if (!attendanceService.hasAccessToCourse(courseId, currentUser)) {
            throw new ServiceException(ErrorCode.AccessDenied);
        }

        return ResponseEntity.ok(attendanceService.getSessionsForCourse(courseId));
    }

    /**
     * Gibt einen einzelnen Termin zurück
     */
    @GetMapping("/sessions/{sessionId}")
    @RequiredRole({Role.ADMIN_ROLE_NAME, Role.COURSE_INSTRUCTOR_ROLE_NAME})
    public ResponseEntity<AttendanceSessionDto> getSession(
            @PathVariable Long sessionId,
            @AuthenticatedUser User currentUser) {

        if (!attendanceService.hasAccessToSession(sessionId, currentUser)) {
            throw new ServiceException(ErrorCode.AccessDenied);
        }

        return ResponseEntity.ok(attendanceService.getSession(sessionId));
    }

    /**
     * Gibt die Anwesenheitseinträge für einen Termin zurück
     */
    @GetMapping("/sessions/{sessionId}/entries")
    @RequiredRole({Role.ADMIN_ROLE_NAME, Role.COURSE_INSTRUCTOR_ROLE_NAME})
    public ResponseEntity<List<AttendanceEntryDto>> getEntriesForSession(
            @PathVariable Long sessionId,
            @AuthenticatedUser User currentUser) {

        if (!attendanceService.hasAccessToSession(sessionId, currentUser)) {
            throw new ServiceException(ErrorCode.AccessDenied);
        }

        return ResponseEntity.ok(attendanceService.getEntriesForSession(sessionId));
    }

    /**
     * Erstellt einen neuen Termin für einen Kurs
     */
    @PostMapping("/sessions")
    @RequiredRole({Role.ADMIN_ROLE_NAME, Role.COURSE_INSTRUCTOR_ROLE_NAME})
    public ResponseEntity<AttendanceSessionDto> createSession(
            @RequestBody CreateAttendanceSessionRequest request,
            @AuthenticatedUser User currentUser) {

        if (!attendanceService.hasAccessToCourse(request.courseId(), currentUser)) {
            throw new ServiceException(ErrorCode.AccessDenied);
        }

        return ResponseEntity.ok(attendanceService.createSession(request));
    }

    /**
     * Aktualisiert einen Termin
     */
    @PutMapping("/sessions/{sessionId}")
    @RequiredRole({Role.ADMIN_ROLE_NAME, Role.COURSE_INSTRUCTOR_ROLE_NAME})
    public ResponseEntity<AttendanceSessionDto> updateSession(
            @PathVariable Long sessionId,
            @RequestBody CreateAttendanceSessionRequest request,
            @AuthenticatedUser User currentUser) {

        if (!attendanceService.hasAccessToSession(sessionId, currentUser)) {
            throw new ServiceException(ErrorCode.AccessDenied);
        }

        return ResponseEntity.ok(attendanceService.updateSession(sessionId, request));
    }

    /**
     * Löscht einen Termin
     */
    @DeleteMapping("/sessions/{sessionId}")
    @RequiredRole({Role.ADMIN_ROLE_NAME, Role.COURSE_INSTRUCTOR_ROLE_NAME})
    public ResponseEntity<Void> deleteSession(
            @PathVariable Long sessionId,
            @AuthenticatedUser User currentUser) {

        if (!attendanceService.hasAccessToSession(sessionId, currentUser)) {
            throw new ServiceException(ErrorCode.AccessDenied);
        }

        attendanceService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Aktualisiert die Anwesenheitseinträge für einen Termin
     */
    @PutMapping("/sessions/{sessionId}/entries")
    @RequiredRole({Role.ADMIN_ROLE_NAME, Role.COURSE_INSTRUCTOR_ROLE_NAME})
    public ResponseEntity<List<AttendanceEntryDto>> updateEntries(
            @PathVariable Long sessionId,
            @RequestBody List<UpdateAttendanceEntryRequest> requests,
            @AuthenticatedUser User currentUser) {

        if (!attendanceService.hasAccessToSession(sessionId, currentUser)) {
            throw new ServiceException(ErrorCode.AccessDenied);
        }

        return ResponseEntity.ok(attendanceService.updateEntries(sessionId, requests));
    }
}