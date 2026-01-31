package sandbox27.ila.backend.absence;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.user.Role;
import sandbox27.infrastructure.security.RequiredRole;

import java.time.LocalDate;
import java.util.List;

/**
 * Controller für die Verwaltung externer Abwesenheiten.
 * Nur für Admins zugänglich.
 */
@RestController
@RequestMapping("/external-absences")
@RequiredArgsConstructor
public class ExternalAbsenceController {

    private final ExternalAbsenceService externalAbsenceService;
    private final BesteSchuleClient besteSchuleClient;

    /**
     * Löst eine manuelle Synchronisation für ein Datum aus.
     */
    @PostMapping("/sync")
    @RequiredRole(Role.ADMIN_ROLE_NAME)
    public ResponseEntity<SyncResultDto> syncAbsences(
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        ExternalAbsenceService.SyncResult result = externalAbsenceService.syncAbsencesForDate(targetDate);
        
        return ResponseEntity.ok(new SyncResultDto(
                targetDate,
                result.totalRelevant(),
                result.created(),
                result.errors(),
                result.message(),
                result.isSuccess()
        ));
    }

    /**
     * Gibt alle gespeicherten Abwesenheiten für ein Datum zurück.
     */
    @GetMapping
    @RequiredRole(Role.ADMIN_ROLE_NAME)
    public ResponseEntity<List<ExternalAbsenceDto>> getAbsences(
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        List<ExternalAbsence> absences = externalAbsenceService.getAbsencesForDate(targetDate);
        
        List<ExternalAbsenceDto> dtos = absences.stream()
                .map(ExternalAbsenceDto::fromEntity)
                .toList();
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Prüft, ob die Beste.Schule API erreichbar ist.
     */
    @GetMapping("/status")
    @RequiredRole(Role.ADMIN_ROLE_NAME)
    public ResponseEntity<ApiStatusDto> getApiStatus() {
        boolean available = besteSchuleClient.isApiAvailable();
        return ResponseEntity.ok(new ApiStatusDto(available, "Beste.Schule API"));
    }

    /**
     * Räumt alte Abwesenheiten auf (manueller Trigger).
     */
    @DeleteMapping("/cleanup")
    @RequiredRole(Role.ADMIN_ROLE_NAME)
    public ResponseEntity<CleanupResultDto> cleanup(
            @RequestParam(defaultValue = "7") int daysToKeep
    ) {
        int deleted = externalAbsenceService.cleanupOldAbsences(daysToKeep);
        return ResponseEntity.ok(new CleanupResultDto(deleted, daysToKeep));
    }

    // DTOs für Controller-Responses

    public record SyncResultDto(
            LocalDate date,
            int totalRelevant,
            int created,
            int errors,
            String message,
            boolean success
    ) {}

    public record ApiStatusDto(
            boolean available,
            String service
    ) {}

    public record CleanupResultDto(
            int deletedCount,
            int daysKept
    ) {}
}
