package sandbox27.ila.backend.attendance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sandbox27.ila.backend.absence.ExternalAbsence;
import sandbox27.ila.backend.absence.ExternalAbsenceService;
import sandbox27.ila.backend.assignments.CourseUserAssignment;
import sandbox27.ila.backend.assignments.CourseUserAssignmentRepository;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseBlockAssignment;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceService {

    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceEntryRepository entryRepository;
    private final CourseRepository courseRepository;
    private final CourseUserAssignmentRepository assignmentRepository;
    private final CourseBlockAssignmentRepository courseBlockAssignmentRepository;
    private final UserRepository userRepository;
    private final ExternalAbsenceService externalAbsenceService;
    private final UnexcusedAbsenceNotificationService notificationService;

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

    /**
     * Gibt die Anwesenheitseinträge für einen Termin zurück.
     * Enthält zusätzlich Informationen über externe Abwesenheiten aus Beste.Schule.
     */
    public List<AttendanceEntryDto> getEntriesForSession(Long sessionId) {
        AttendanceSession session = sessionRepository.findByIdWithEntries(sessionId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));

        // Kursstartzeit ermitteln für die Abwesenheitsprüfung
        LocalDate sessionDate = session.getDate();
        LocalTime courseStartTime = getCourseStartTime(session.getCourse());
        LocalDateTime checkTime = LocalDateTime.of(sessionDate, courseStartTime);

        // Alle UserNames sammeln
        List<String> userNames = session.getEntries().stream()
                .map(e -> e.getUser().getUserName())
                .toList();

        // Externe Abwesenheiten für alle Teilnehmer auf einmal laden
        Map<String, Optional<ExternalAbsence>> externalAbsences =
                externalAbsenceService.getAbsencesForUsers(userNames, checkTime);

        return session.getEntries().stream()
                .map(entry -> {
                    ExternalAbsence absence = externalAbsences
                            .getOrDefault(entry.getUser().getUserName(), Optional.empty())
                            .orElse(null);
                    return AttendanceEntryDto.fromEntity(entry, absence);
                })
                .sorted((a, b) -> {
                    int lastNameCompare = a.lastName().compareToIgnoreCase(b.lastName());
                    if (lastNameCompare != 0) return lastNameCompare;
                    return a.firstName().compareToIgnoreCase(b.firstName());
                })
                .collect(Collectors.toList());
    }

    /**
     * Erstellt einen neuen Termin für einen Kurs.
     * Schüler, die in Beste.Schule als abwesend gemeldet sind, werden automatisch
     * als abwesend vormarkiert.
     */
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

        // Kursstartzeit für Abwesenheitsprüfung ermitteln
        LocalTime courseStartTime = getCourseStartTime(course);
        LocalDateTime checkTime = LocalDateTime.of(request.date(), courseStartTime);

        // Alle aktuellen Kursteilnehmer laden
        List<CourseUserAssignment> assignments = assignmentRepository
                .findByCourse_idOrderByUser_LastName(course.getId());

        // UserNames sammeln
        List<String> userNames = assignments.stream()
                .map(a -> a.getUser().getUserName())
                .toList();

        // Externe Abwesenheiten für alle Teilnehmer auf einmal laden
        Map<String, Optional<ExternalAbsence>> externalAbsences =
                externalAbsenceService.getAbsencesForUsers(userNames, checkTime);

        for (CourseUserAssignment assignment : assignments) {
            User user = assignment.getUser();

            // Prüfen ob extern als abwesend gemeldet
            Optional<ExternalAbsence> externalAbsence = externalAbsences
                    .getOrDefault(user.getUserName(), Optional.empty());
            boolean isExternallyAbsent = externalAbsence.isPresent();

            // Wenn extern abwesend -> als abwesend markieren mit Notiz, sonst anwesend
            String note = null;
            if (isExternallyAbsent) {
                String absenceType = externalAbsence.get().getAbsenceType();
                note = "In Beste.Schule gemeldet: " + (absenceType != null ? absenceType : "abwesend");
            }

            AttendanceEntry entry = AttendanceEntry.builder()
                    .session(session)
                    .user(user)
                    .present(!isExternallyAbsent)
                    .note(note)
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

    /**
     * Aktualisiert die Anwesenheitseinträge für einen Termin.
     *
     * Verhalten der drei Übergänge:
     *   anwesend -> abwesend:
     *     Wenn weder extern (Sekretariat) noch von uns selbst bereits in
     *     Beste.Schule eingetragen, wird die Abwesenheit dort eingetragen.
     *     Die zurückgegebene Absence-ID wird am AttendanceEntry persistiert
     *     (besteSchuleAbsenceId), wodurch wiederholte Speicherungen derselben
     *     Session keine Doppelmeldungen mehr erzeugen.
     *
     *   abwesend -> anwesend:
     *     Wenn die Abwesenheit von uns selbst angelegt wurde (besteSchuleAbsenceId
     *     gesetzt), wird sie in Beste.Schule storniert. Bei extern gemeldeter
     *     Abwesenheit (z.B. durch die Eltern) wird eine Warnung ans Frontend
     *     zurückgegeben — wir dürfen fremde Einträge nicht löschen, weil sie
     *     für andere Zeiträume des Tages gelten könnten.
     *
     *   sonst:
     *     Nur Status/Notiz aktualisieren.
     */
    @Transactional
    public UpdateAttendanceEntriesResult updateEntries(Long sessionId,
                                                       List<UpdateAttendanceEntryRequest> requests,
                                                       User reportingUser) {
        AttendanceSession session = sessionRepository.findByIdWithEntries(sessionId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));

        Course course = session.getCourse();
        LocalDate sessionDate = session.getDate();

        // Block-Informationen für Zeitprüfung
        Block block = getCourseBlock(course);
        LocalTime courseStartTime = block != null ? block.getStartTime() : LocalTime.of(10, 0);
        LocalTime courseEndTime = block != null ? block.getEndTime() : LocalTime.of(11, 30);
        LocalDateTime checkTime = LocalDateTime.of(sessionDate, courseStartTime);

        String dayOfWeek = sessionDate.getDayOfWeek()
                .getDisplayName(TextStyle.FULL, Locale.GERMAN);

        // Bestehende Einträge als Map für schnellen Zugriff
        Map<String, AttendanceEntry> existingEntries = session.getEntries().stream()
                .collect(Collectors.toMap(e -> e.getUser().getUserName(), Function.identity()));

        // Externe Abwesenheiten für ALLE betroffenen User laden — wir brauchen
        // sie sowohl beim Wechsel "anwesend -> abwesend" (Doppelmeldung verhindern)
        // als auch beim Wechsel "abwesend -> anwesend" (Storno-Schutz für fremde
        // Einträge).
        List<String> userNamesToCheck = requests.stream()
                .map(UpdateAttendanceEntryRequest::userName)
                .toList();

        Map<String, Optional<ExternalAbsence>> externalAbsences =
                externalAbsenceService.getAbsencesForUsers(userNamesToCheck, checkTime);

        List<String> warnings = new ArrayList<>();

        for (UpdateAttendanceEntryRequest request : requests) {
            AttendanceEntry entry = existingEntries.get(request.userName());

            if (entry != null) {
                boolean wasPresent = entry.isPresent();
                boolean isNowPresent = request.present();

                if (wasPresent && !isNowPresent) {
                    // anwesend -> abwesend
                    reportAbsenceIfNeeded(entry, course, sessionDate,
                            courseStartTime, courseEndTime, dayOfWeek,
                            externalAbsences, reportingUser);
                } else if (!wasPresent && isNowPresent) {
                    // abwesend -> anwesend
                    handleReturnToPresent(entry, externalAbsences, warnings);
                }

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

                // Bei einem neuen Eintrag, der direkt abwesend angelegt wird:
                // ggf. an Beste.Schule melden.
                if (!request.present()) {
                    reportAbsenceIfNeeded(newEntry, course, sessionDate,
                            courseStartTime, courseEndTime, dayOfWeek,
                            externalAbsences, reportingUser);
                    // Erneut speichern, damit eine evtl. gesetzte Absence-ID persistiert wird
                    entryRepository.save(newEntry);
                }
            }
        }

        return new UpdateAttendanceEntriesResult(getEntriesForSession(sessionId), warnings);
    }

    /**
     * "anwesend -> abwesend": Trägt eine Abwesenheit in Beste.Schule ein, sofern
     * noch keine Meldung für diesen Eintrag bekannt ist (weder von uns noch
     * extern). Persistiert die zurückgegebene Absence-ID am übergebenen Entry,
     * damit zukünftige Speicherungen derselben Session keine Doppelmeldungen
     * erzeugen.
     */
    private void reportAbsenceIfNeeded(AttendanceEntry entry,
                                       Course course,
                                       LocalDate sessionDate,
                                       LocalTime courseStartTime,
                                       LocalTime courseEndTime,
                                       String dayOfWeek,
                                       Map<String, Optional<ExternalAbsence>> externalAbsences,
                                       User reportingUser) {

        // Schon von uns gemeldet? -> ID ist persistiert, nichts zu tun.
        if (entry.getBesteSchuleAbsenceId() != null) {
            log.debug("Abwesenheit für {} in Session {} bereits in Beste.Schule eingetragen (Absence-ID {}) — überspringe.",
                    entry.getUser().getUserName(),
                    entry.getSession().getId(),
                    entry.getBesteSchuleAbsenceId());
            return;
        }

        // Extern (Sekretariat) bereits gemeldet? -> nicht doppelt eintragen.
        boolean isExternallyAbsent = externalAbsences
                .getOrDefault(entry.getUser().getUserName(), Optional.empty())
                .isPresent();
        if (isExternallyAbsent) {
            log.debug("Abwesenheit für {} ist bereits extern in Beste.Schule erfasst — überspringe.",
                    entry.getUser().getUserName());
            return;
        }

        User student = entry.getUser();
        UnexcusedAbsenceInfo absenceInfo = UnexcusedAbsenceInfo.create(
                student, course, sessionDate, courseStartTime, courseEndTime, dayOfWeek,
                reportingUser);

        log.info("Unentschuldigte Abwesenheit erkannt: {} in Kurs {} am {} — wird an Beste.Schule gemeldet.",
                student.getFirstName() + " " + student.getLastName(),
                course.getName(),
                sessionDate);

        Optional<Long> absenceId = notificationService.notifyUnexcusedAbsence(absenceInfo);
        absenceId.ifPresent(entry::setBesteSchuleAbsenceId);
    }

    /**
     * "abwesend -> anwesend": Storno der eigenen BS-Eintragung; bei extern
     * gemeldeter Abwesenheit eine Warning sammeln. Fremde Einträge werden
     * grundsätzlich nicht gelöscht, weil sie für andere Zeiträume des Tages
     * gelten könnten.
     */
    private void handleReturnToPresent(AttendanceEntry entry,
                                       Map<String, Optional<ExternalAbsence>> externalAbsences,
                                       List<String> warnings) {

        Long absenceId = entry.getBesteSchuleAbsenceId();
        String studentName = entry.getUser().getFirstName() + " " + entry.getUser().getLastName();

        if (absenceId != null) {
            // Eigene Eintragung -> Storno versuchen
            String courseName = entry.getSession().getCourse().getName();
            boolean ok = notificationService.cancelAbsence(absenceId, studentName, courseName);
            if (ok) {
                entry.setBesteSchuleAbsenceId(null);
            } else {
                warnings.add(String.format(
                        "Die Abwesenheit von %s konnte in Beste.Schule nicht entfernt werden " +
                                "(technischer Fehler). Der Admin wurde informiert.",
                        studentName));
            }
            return;
        }

        // Keine eigene ID -> wenn extern abwesend, dürfen wir nicht löschen.
        boolean isExternallyAbsent = externalAbsences
                .getOrDefault(entry.getUser().getUserName(), Optional.empty())
                .isPresent();
        if (isExternallyAbsent) {
            warnings.add(String.format(
                    "Die Abwesenheit von %s wurde extern in Beste.Schule eingetragen " +
                            "(z.B. durch die Eltern) und kann nicht aus der iLA-App entfernt werden. " +
                            "Bei Bedarf bitte das Sekretariat kontaktieren.",
                    studentName));
        }
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

    /**
     * Ermittelt die Startzeit des Kurses aus dem zugewiesenen Block.
     * Fallback: 10:00 Uhr wenn kein Block zugewiesen ist.
     */
    private LocalTime getCourseStartTime(Course course) {
        Block block = getCourseBlock(course);
        return block != null ? block.getStartTime() : LocalTime.of(10, 0);
    }

    /**
     * Ermittelt den Block des Kurses.
     */
    private Block getCourseBlock(Course course) {
        return courseBlockAssignmentRepository.findAllByCourse(course).stream()
                .findFirst()
                .map(CourseBlockAssignment::getBlock)
                .orElse(null);
    }
}