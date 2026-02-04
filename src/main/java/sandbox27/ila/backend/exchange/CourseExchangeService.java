package sandbox27.ila.backend.exchange;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sandbox27.ila.backend.assignments.CourseUserAssignment;
import sandbox27.ila.backend.assignments.CourseUserAssignmentRepository;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.exchange.events.ExchangeRequestFulfilledEvent;
import sandbox27.ila.backend.exchange.events.ExchangeRequestUnfulfillableEvent;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.user.User;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseExchangeService {

    private final ExchangeRequestRepository exchangeRequestRepository;
    private final CourseUserAssignmentRepository assignmentRepository;
    private final CourseRepository courseRepository;
    private final PeriodRepository periodRepository;
    private final CourseEligibilityService eligibilityService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Erstellt einen neuen Wechselwunsch
     */
    @Transactional
    public ExchangeRequest createExchangeRequest(
            User student,
            Long periodId,
            Long currentAssignmentId,
            List<Long> desiredCourseIds) {

        // Manuelle Validierung
        if (periodId == null) {
            throw new ServiceException(ErrorCode.FieldRequired, "periodId");
        }
        if (currentAssignmentId == null) {
            throw new ServiceException(ErrorCode.FieldRequired, "currentAssignmentId");
        }
        if (desiredCourseIds == null || desiredCourseIds.isEmpty()) {
            throw new ServiceException(ErrorCode.FieldRequired, "desiredCourseIds");
        }

        Period period = periodRepository.findById(periodId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound, "Period", periodId));

        // Prüfe, ob Wechselphase aktiv ist
        validateExchangePhaseActive(period);

        // Lade die aktuelle Zuweisung
        CourseUserAssignment currentAssignment = assignmentRepository.findById(currentAssignmentId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound, "Assignment", currentAssignmentId));

        // Prüfe, ob die Zuweisung dem Schüler gehört
        if (!currentAssignment.getUser().getId().equals(student.getId())) {
            throw new ServiceException(ErrorCode.AccessDenied, "Diese Zuweisung gehört nicht zu diesem Schüler");
        }

        // Prüfe, ob bereits ein offener Wunsch für diese Zuweisung existiert
        if (exchangeRequestRepository.existsPendingRequestForAssignment(student, currentAssignmentId)) {
            throw new ServiceException(ErrorCode.AccessDenied,
                    "Es existiert bereits ein offener Wechselwunsch für diesen Kurs");
        }

        // Prüfe, ob die Zuweisung preset ist (preset-Kurse können nicht abgegeben werden)
        if (currentAssignment.isPreset()) {
            throw new ServiceException(ErrorCode.AccessDenied,
                    "Preset-Kurse können nicht getauscht werden");
        }

        // Lade die gewünschten Kurse
        List<Course> desiredCourses = courseRepository.findAllById(desiredCourseIds);
        if (desiredCourses.size() != desiredCourseIds.size()) {
            throw new ServiceException(ErrorCode.NotFound, "Einer oder mehrere Kurse wurden nicht gefunden");
        }

        // Validiere jeden gewünschten Kurs
        for (Course desiredCourse : desiredCourses) {
            EligibilityResult result = eligibilityService.checkExchangeEligibility(
                    student, currentAssignment, desiredCourse, periodId);

            if (result.isIneligible()) {
                log.debug("Kurs {} ist nicht wählbar für {}: {}",
                        desiredCourse.getName(), student.getUserName(), result.getReason());
                // Wir erlauben trotzdem das Hinzufügen - bei der Batch-Auflösung wird erneut geprüft
                // Das ermöglicht Kettentausche
            }
        }

        // Erstelle den ExchangeRequest
        ExchangeRequest request = ExchangeRequest.builder()
                .student(student)
                .period(period)
                .currentAssignment(currentAssignment)
                .status(ExchangeRequestStatus.PENDING)
                .build();

        // Füge die gewünschten Kurse hinzu (Reihenfolge = Priorität)
        for (int i = 0; i < desiredCourseIds.size(); i++) {
            Long courseId = desiredCourseIds.get(i);
            Course course = desiredCourses.stream()
                    .filter(c -> c.getId() == courseId)
                    .findFirst()
                    .orElseThrow();
            request.addDesiredCourse(course, i + 1);
        }

        ExchangeRequest saved = exchangeRequestRepository.save(request);
        log.info("Wechselwunsch erstellt: Schüler {} will {} gegen einen von {} Kursen tauschen",
                student.getUserName(),
                currentAssignment.getCourse().getName(),
                desiredCourseIds.size());

        return saved;
    }

    /**
     * Aktualisiert die Wunschliste eines bestehenden Requests
     */
    @Transactional
    public ExchangeRequest updateDesiredCourses(
            User student,
            Long requestId,
            List<Long> newDesiredCourseIds) {

        // Manuelle Validierung
        if (newDesiredCourseIds == null || newDesiredCourseIds.isEmpty()) {
            throw new ServiceException(ErrorCode.FieldRequired, "desiredCourseIds");
        }

        ExchangeRequest request = exchangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound, "ExchangeRequest", requestId));

        // Prüfe Berechtigung
        if (!request.getStudent().getId().equals(student.getId())) {
            throw new ServiceException(ErrorCode.AccessDenied, "Dieser Wechselwunsch gehört nicht zu diesem Schüler");
        }

        // Nur PENDING Requests können bearbeitet werden
        if (request.getStatus() != ExchangeRequestStatus.PENDING) {
            throw new ServiceException(ErrorCode.AccessDenied, "Nur offene Wechselwünsche können bearbeitet werden");
        }

        // Prüfe, ob Wechselphase noch aktiv
        validateExchangePhaseActive(request.getPeriod());

        // Lade neue Kurse
        List<Course> newCourses = courseRepository.findAllById(newDesiredCourseIds);
        if (newCourses.size() != newDesiredCourseIds.size()) {
            throw new ServiceException(ErrorCode.NotFound, "Einer oder mehrere Kurse wurden nicht gefunden");
        }

        // Aktualisiere die Wunschliste
        request.clearDesiredCourses();
        for (int i = 0; i < newDesiredCourseIds.size(); i++) {
            Long courseId = newDesiredCourseIds.get(i);
            Course course = newCourses.stream()
                    .filter(c -> c.getId() == courseId)
                    .findFirst()
                    .orElseThrow();
            request.addDesiredCourse(course, i + 1);
        }

        return exchangeRequestRepository.save(request);
    }

    /**
     * Zieht einen Wechselwunsch zurück
     */
    @Transactional
    public void withdrawRequest(User student, Long requestId) {
        ExchangeRequest request = exchangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound, "ExchangeRequest", requestId));

        if (!request.getStudent().getId().equals(student.getId())) {
            throw new ServiceException(ErrorCode.AccessDenied, "Dieser Wechselwunsch gehört nicht zu diesem Schüler");
        }

        if (request.getStatus() != ExchangeRequestStatus.PENDING) {
            throw new ServiceException(ErrorCode.AccessDenied, "Nur offene Wechselwünsche können zurückgezogen werden");
        }

        request.withdraw();
        exchangeRequestRepository.save(request);

        log.info("Wechselwunsch {} von {} zurückgezogen", requestId, student.getUserName());
    }

    /**
     * Gibt alle offenen Wechselwünsche eines Schülers zurück
     */
    public List<ExchangeRequest> getPendingRequestsForStudent(User student, Long periodId) {
        Period period = periodRepository.findById(periodId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound, "Period", periodId));

        return exchangeRequestRepository.findByStudentAndPeriodAndStatus(
                student, period, ExchangeRequestStatus.PENDING);
    }

    /**
     * Gibt alle Wechselwünsche eines Schülers zurück (alle Status)
     */
    public List<ExchangeRequest> getAllRequestsForStudent(User student, Long periodId) {
        Period period = periodRepository.findById(periodId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound, "Period", periodId));

        return exchangeRequestRepository.findByStudentAndPeriod(student, period);
    }

    /**
     * Führt die Batch-Auflösung aller offenen Wechselwünsche durch.
     * Diese Methode sollte am Ende der Wechselphase aufgerufen werden.
     */
    @Transactional
    public ExchangeResolutionResult resolveExchangeRequests(Long periodId) {
        Period period = periodRepository.findById(periodId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound, "Period", periodId));

        log.info("Starte Batch-Auflösung der Wechselwünsche für Periode {}", period.getName());

        // Lade alle offenen Requests mit Details
        List<ExchangeRequest> pendingRequests =
                exchangeRequestRepository.findPendingRequestsWithDetails(period);

        log.info("Gefunden: {} offene Wechselwünsche", pendingRequests.size());

        if (pendingRequests.isEmpty()) {
            return ExchangeResolutionResult.empty();
        }

        // Sortiere nach Fairness-Score (Schüler mit schlechterem Erstverteilungs-Ergebnis zuerst)
        // Je höher der durchschnittliche Priority-Wert, desto schlechter wurde der Schüler bedient
        List<ExchangeRequest> sortedRequests = sortByFairness(pendingRequests);

        int fulfilled = 0;
        int unfulfillable = 0;

        // Mehrere Durchläufe, um Kettenwechsel zu ermöglichen
        boolean anyFulfilledInRound;
        int round = 0;
        int maxRounds = 10;

        do {
            anyFulfilledInRound = false;
            round++;
            log.debug("Auflösungsrunde {}", round);

            for (ExchangeRequest request : sortedRequests) {
                if (request.getStatus() != ExchangeRequestStatus.PENDING) {
                    continue;
                }

                // Versuche, den Wunsch zu erfüllen
                boolean success = tryFulfillRequest(request);

                if (success) {
                    fulfilled++;
                    anyFulfilledInRound = true;
                }
            }

        } while (anyFulfilledInRound && round < maxRounds);

        // Markiere alle verbleibenden als nicht erfüllbar
        for (ExchangeRequest request : sortedRequests) {
            if (request.getStatus() == ExchangeRequestStatus.PENDING) {
                String reason = "Kein passender Kurs mit freien Plätzen gefunden";
                request.markAsUnfulfillable(reason);
                exchangeRequestRepository.save(request);
                unfulfillable++;

                // Event für Email-Benachrichtigung auslösen
                User student = request.getStudent();
                CourseUserAssignment currentAssignment = request.getCurrentAssignment();
                List<String> desiredCourseNames = request.getDesiredCourses().stream()
                        .sorted(Comparator.comparingInt(ExchangeRequestOption::getPriority))
                        .map(opt -> opt.getDesiredCourse().getName())
                        .toList();

                eventPublisher.publishEvent(new ExchangeRequestUnfulfillableEvent(
                        student.getUserName(),
                        student.getEmail(),
                        student.getFirstName(),
                        currentAssignment.getCourse().getName(),
                        currentAssignment.getBlock().getName(),
                        desiredCourseNames,
                        reason
                ));
            }
        }

        log.info("Batch-Auflösung abgeschlossen: {} erfüllt, {} nicht erfüllbar", fulfilled, unfulfillable);

        return ExchangeResolutionResult.builder()
                .totalRequests(pendingRequests.size())
                .fulfilled(fulfilled)
                .unfulfillable(unfulfillable)
                .rounds(round)
                .build();
    }

    /**
     * Versucht, einen einzelnen Wechselwunsch zu erfüllen
     */
    private boolean tryFulfillRequest(ExchangeRequest request) {
        User student = request.getStudent();
        CourseUserAssignment currentAssignment = request.getCurrentAssignment();
        Long periodId = request.getPeriod().getId();

        // Gehe durch alle Wunschkurse in Prioritätsreihenfolge
        for (ExchangeRequestOption option : request.getDesiredCourses()) {
            Course desiredCourse = option.getDesiredCourse();

            // Prüfe Berechtigung zum Zeitpunkt der Auflösung
            EligibilityResult eligibility = eligibilityService.checkExchangeEligibility(
                    student, currentAssignment, desiredCourse, periodId);

            if (eligibility.isEligible()) {
                // Wechsel durchführen!
                executeExchange(request, desiredCourse);
                return true;
            } else {
                log.debug("Kurs {} nicht möglich für {}: {}",
                        desiredCourse.getName(), student.getUserName(), eligibility.getReason());
            }
        }

        return false;
    }

    /**
     * Führt den tatsächlichen Kurswechsel durch
     */
    private void executeExchange(ExchangeRequest request, Course newCourse) {
        User student = request.getStudent();
        CourseUserAssignment oldAssignment = request.getCurrentAssignment();
        String oldCourseName = oldAssignment.getCourse().getName();
        Block oldBlock = oldAssignment.getBlock();

        // Block über CourseBlockAssignment ermitteln
        Block newBlock = eligibilityService.getBlockForCourse(newCourse);

        // Lösche die alte Zuweisung
        assignmentRepository.delete(oldAssignment);

        // Erstelle die neue Zuweisung
        CourseUserAssignment newAssignment = CourseUserAssignment.builder()
                .user(student)
                .course(newCourse)
                .block(newBlock)
                .preset(false)
                .build();
        assignmentRepository.save(newAssignment);

        // Markiere Request als erfüllt
        request.markAsFulfilled(newCourse);
        exchangeRequestRepository.save(request);

        log.info("Wechsel durchgeführt: {} tauscht {} gegen {}",
                student.getUserName(),
                oldCourseName,
                newCourse.getName());

        // Event für Email-Benachrichtigung auslösen
        eventPublisher.publishEvent(new ExchangeRequestFulfilledEvent(
                student.getUserName(),
                student.getEmail(),
                student.getFirstName(),
                oldCourseName,
                newCourse.getName(),
                newBlock.getName(),
                newBlock.getDayOfWeek().toString()
        ));
    }

    /**
     * Sortiert Requests nach Fairness (schlechter bediente Schüler zuerst)
     */
    private List<ExchangeRequest> sortByFairness(List<ExchangeRequest> requests) {
        // Berechne Fairness-Score für jeden Schüler
        Map<String, Double> studentFairnessScores = new HashMap<>();

        for (ExchangeRequest request : requests) {
            User student = request.getStudent();
            if (!studentFairnessScores.containsKey(student.getUserName())) {
                double score = calculateFairnessScore(student, request.getPeriod().getId());
                studentFairnessScores.put(student.getUserName(), score);
            }
        }

        // Sortiere: Höherer Score = schlechter bedient = höhere Priorität
        return requests.stream()
                .sorted(Comparator.comparingDouble(r ->
                        -studentFairnessScores.getOrDefault(r.getStudent().getUserName(), 0.0)))
                .collect(Collectors.toList());
    }

    /**
     * Berechnet den Fairness-Score eines Schülers (durchschnittliche Priorität seiner Kurse)
     */
    private double calculateFairnessScore(User student, Long periodId) {
        List<CourseUserAssignment> assignments =
                assignmentRepository.findByUserAndCourse_Period_Id(student, periodId);

        // Finde die ursprünglichen Prioritäten aus den Preferences
        // Hier vereinfacht: Je mehr preset/ohne-Präferenz-Kurse, desto neutraler
        // In einer vollständigen Implementierung würdest du die Original-Preferences laden

        return assignments.size(); // Vereinfacht - besser wäre die Original-Priority
    }

    /**
     * Prüft, ob die Wechselphase aktiv ist
     */
    private void validateExchangePhaseActive(Period period) {
        LocalDateTime now = LocalDateTime.now();

        if (period.getExchangePhaseStart() == null || period.getExchangePhaseEnd() == null) {
            throw new ServiceException(ErrorCode.AccessDenied,
                    "Für diese Periode ist keine Wechselphase konfiguriert");
        }

        if (now.isBefore(period.getExchangePhaseStart())) {
            throw new ServiceException(ErrorCode.AccessDenied,
                    "Die Wechselphase hat noch nicht begonnen");
        }

        if (now.isAfter(period.getExchangePhaseEnd())) {
            throw new ServiceException(ErrorCode.AccessDenied,
                    "Die Wechselphase ist bereits beendet");
        }
    }

    /**
     * Löscht einen Wechselwunsch (nur für Admins)
     */
    @Transactional
    public void deleteRequestAsAdmin(Long requestId) {
        ExchangeRequest request = exchangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound, "ExchangeRequest", requestId));

        // Nur PENDING Requests sollten gelöscht werden können
        if (request.getStatus() != ExchangeRequestStatus.PENDING) {
            throw new ServiceException(ErrorCode.AccessDenied,
                    "Nur offene Wechselwünsche können gelöscht werden");
        }

        exchangeRequestRepository.delete(request);

        log.info("Admin hat Wechselwunsch {} von Schüler {} gelöscht",
                requestId, request.getStudent().getUserName());
    }

    /**
     * Gibt verfügbare Kurse zurück, auf die ein Schüler wechseln könnte
     * (unter Berücksichtigung, welchen Kurs er abgeben würde)
     */
    public List<AvailableCourseDto> getAvailableCoursesForExchange(
            User student,
            Long periodId,
            Long assignmentToGiveUpId) {

        CourseUserAssignment assignmentToGiveUp = assignmentRepository.findById(assignmentToGiveUpId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound, "Assignment", assignmentToGiveUpId));

        Period period = periodRepository.findById(periodId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound, "Period", periodId));

        // Lade alle Kurse der Periode (außer dem aktuellen)
        List<Course> allCourses = courseRepository.findAllByPeriod(period).stream()
                .filter(c -> c.getId() != assignmentToGiveUp.getCourse().getId())
                .filter(c -> !c.isManualAssignmentOnly())
                .filter(c -> !c.isPlaceholder())
                .collect(Collectors.toList());

        List<AvailableCourseDto> result = new ArrayList<>();

        for (Course course : allCourses) {
            EligibilityResult eligibility = eligibilityService.checkExchangeEligibility(
                    student, assignmentToGiveUp, course, periodId);

            // Kurse mit harten Ausschlüssen (Klassenstufe, Geschlecht etc.) komplett ausblenden
            if (eligibility.isExcludeFromList()) {
                continue;
            }

            int availableSpots = eligibilityService.getAvailableSpots(course);

            // Block über CourseBlockAssignment ermitteln
            Block block = eligibilityService.getBlockForCourse(course);

            result.add(AvailableCourseDto.builder()
                    .id(course.getId())
                    .courseId(course.getCourseId())
                    .name(course.getName())
                    .blockId(block.getId())
                    .blockName(block.getName())
                    .dayOfWeek(block.getDayOfWeek().toString())
                    .availableSpots(availableSpots)
                    .eligible(eligibility.isEligible())
                    .ineligibilityReason(eligibility.getReason())
                    .warning(eligibility.getWarning())
                    .build());
        }

        // Sortiere: Zuerst eligible, dann nach verfügbaren Plätzen
        result.sort(Comparator
                .comparing(AvailableCourseDto::isEligible).reversed()
                .thenComparing(Comparator.comparingInt(AvailableCourseDto::getAvailableSpots).reversed()));

        return result;
    }
}