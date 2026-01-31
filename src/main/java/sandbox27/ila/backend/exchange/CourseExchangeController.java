package sandbox27.ila.backend.exchange;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.user.Role;
import sandbox27.ila.backend.user.User;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;
import sandbox27.infrastructure.security.AuthenticatedUser;
import sandbox27.infrastructure.security.RequiredRole;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/exchange")
@RequiredArgsConstructor
@Slf4j
public class CourseExchangeController {

    private final CourseExchangeService exchangeService;
    private final ExchangeRequestRepository exchangeRequestRepository;
    private final PeriodRepository periodRepository;
    private final CourseEligibilityService eligibilityService;

    // ==================== DTOs ====================

    public record CreateExchangeRequestDto(
            Long periodId,
            Long currentAssignmentId,
            List<Long> desiredCourseIds
    ) {}

    public record UpdateExchangeRequestDto(
            List<Long> desiredCourseIds
    ) {}

    public record ExchangeRequestResponseDto(
            Long id,
            String studentName,
            String currentCourseName,
            String currentBlockName,
            List<DesiredCourseOptionDto> desiredCourses,
            String status,
            String fulfilledWithCourseName,
            String rejectionReason,
            String createdAt,
            String resolvedAt
    ) {}

    public record DesiredCourseOptionDto(
            Long id,        // Course.id (eindeutige interne ID)
            String courseId, // Course.courseId (fachliche Kennung)
            String name,
            String blockName,
            int priority
    ) {}

    // ==================== Schüler-Endpoints ====================

    /**
     * Erstellt einen neuen Wechselwunsch
     */
    @PostMapping
    @RequiredRole(Role.STUDENT_ROLE_NAME)
    public ResponseEntity<ExchangeRequestResponseDto> createRequest(
            @AuthenticatedUser User student,
            @RequestBody CreateExchangeRequestDto dto) {

        ExchangeRequest request = exchangeService.createExchangeRequest(
                student,
                dto.periodId(),
                dto.currentAssignmentId(),
                dto.desiredCourseIds()
        );

        return ResponseEntity.ok(toResponseDto(request));
    }

    /**
     * Aktualisiert die Wunschliste eines bestehenden Requests
     */
    @PutMapping("/{requestId}")
    @RequiredRole(Role.STUDENT_ROLE_NAME)
    public ResponseEntity<ExchangeRequestResponseDto> updateRequest(
            @AuthenticatedUser User student,
            @PathVariable Long requestId,
            @RequestBody UpdateExchangeRequestDto dto) {

        ExchangeRequest request = exchangeService.updateDesiredCourses(
                student, requestId, dto.desiredCourseIds());

        return ResponseEntity.ok(toResponseDto(request));
    }

    /**
     * Zieht einen Wechselwunsch zurück
     */
    @DeleteMapping("/{requestId}")
    @RequiredRole(Role.STUDENT_ROLE_NAME)
    public ResponseEntity<Void> withdrawRequest(
            @AuthenticatedUser User student,
            @PathVariable Long requestId) {

        exchangeService.withdrawRequest(student, requestId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Gibt alle Wechselwünsche des eingeloggten Schülers zurück
     */
    @GetMapping("/my-requests")
    @RequiredRole(Role.STUDENT_ROLE_NAME)
    public ResponseEntity<List<ExchangeRequestResponseDto>> getMyRequests(
            @AuthenticatedUser User student,
            @RequestParam Long periodId) {

        List<ExchangeRequest> requests = exchangeService.getAllRequestsForStudent(student, periodId);

        return ResponseEntity.ok(requests.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList()));
    }

    /**
     * Gibt verfügbare Kurse für einen Wechsel zurück
     */
    @GetMapping("/available-courses")
    @RequiredRole(Role.STUDENT_ROLE_NAME)
    public ResponseEntity<List<AvailableCourseDto>> getAvailableCourses(
            @AuthenticatedUser User student,
            @RequestParam Long periodId,
            @RequestParam Long assignmentId) {

        List<AvailableCourseDto> courses = exchangeService.getAvailableCoursesForExchange(
                student, periodId, assignmentId);

        return ResponseEntity.ok(courses);
    }

    // ==================== Admin-Endpoints ====================

    /**
     * Gibt alle offenen Wechselwünsche einer Periode zurück (Admin)
     */
    @GetMapping("/admin/pending")
    @RequiredRole(Role.ADMIN_ROLE_NAME)
    public ResponseEntity<List<ExchangeRequestResponseDto>> getPendingRequests(
            @RequestParam Long periodId) {

        List<ExchangeRequest> requests = exchangeRequestRepository
                .findByPeriodAndStatusWithOptions(
                        periodRepository.findById(periodId)
                                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound, "Period", periodId)),
                        ExchangeRequestStatus.PENDING);

        return ResponseEntity.ok(requests.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList()));
    }

    /**
     * Führt die Batch-Auflösung durch (Admin)
     */
    @PostMapping("/admin/resolve")
    @RequiredRole(Role.ADMIN_ROLE_NAME)
    public ResponseEntity<ExchangeResolutionResult> resolveExchanges(
            @RequestParam Long periodId) {

        ExchangeResolutionResult result = exchangeService.resolveExchangeRequests(periodId);
        return ResponseEntity.ok(result);
    }

    /**
     * Gibt alle Wechselwünsche einer Periode zurück (Admin)
     */
    @GetMapping("/admin/all")
    @RequiredRole(Role.ADMIN_ROLE_NAME)
    public ResponseEntity<List<ExchangeRequestResponseDto>> getAllRequests(
            @RequestParam Long periodId) {

        List<ExchangeRequest> requests = exchangeRequestRepository
                .findByPeriod(periodRepository.findById(periodId)
                        .orElseThrow(() -> new ServiceException(ErrorCode.NotFound, "Period", periodId)));

        return ResponseEntity.ok(requests.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList()));
    }

    // ==================== Helper ====================

    private ExchangeRequestResponseDto toResponseDto(ExchangeRequest request) {
        return new ExchangeRequestResponseDto(
                request.getId(),
                request.getStudent().getUserName(),
                request.getCurrentAssignment().getCourse().getName(),
                request.getCurrentAssignment().getBlock().getName(),
                request.getDesiredCourses().stream()
                        .map(this::toOptionDto)
                        .collect(Collectors.toList()),
                request.getStatus().name(),
                request.getFulfilledWithCourse() != null
                        ? request.getFulfilledWithCourse().getName()
                        : null,
                request.getRejectionReason(),
                request.getCreatedAt() != null
                        ? request.getCreatedAt().toString()
                        : null,
                request.getResolvedAt() != null
                        ? request.getResolvedAt().toString()
                        : null
        );
    }

    private DesiredCourseOptionDto toOptionDto(ExchangeRequestOption option) {
        sandbox27.ila.backend.block.Block block = eligibilityService.getBlockForCourse(option.getDesiredCourse());
        return new DesiredCourseOptionDto(
                option.getDesiredCourse().getId(),
                option.getDesiredCourse().getCourseId(),
                option.getDesiredCourse().getName(),
                block.getName(),
                option.getPriority()
        );
    }

    private String getBlockNameForCourse(sandbox27.ila.backend.course.Course course) {
        return eligibilityService.getBlockForCourse(course).getName();
    }
}