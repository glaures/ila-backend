package sandbox27.ila.backend.exclusion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.user.Role;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;
import sandbox27.infrastructure.security.RequiredRole;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/block-exclusions")
@RequiredArgsConstructor
@Slf4j
public class UserBlockExclusionController {

    private final UserBlockExclusionService exclusionService;

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @GetMapping
    public ResponseEntity<List<UserBlockExclusionDTO>> getExclusions(
            @RequestParam Long periodId) {
        List<UserBlockExclusionDTO> exclusions = exclusionService.getExclusionsForPeriod(periodId);
        return ResponseEntity.ok(exclusions);
    }

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @GetMapping("/user/{userName}")
    public ResponseEntity<Set<Long>> getExcludedBlocksForUser(
            @PathVariable String userName,
            @RequestParam Long periodId) {
        Set<Long> excludedBlocks = exclusionService.getExcludedBlockIds(userName, periodId);
        return ResponseEntity.ok(excludedBlocks);
    }

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @PostMapping
    public ResponseEntity<?> addExclusion(@RequestBody AddExclusionRequest request) {
        if (!request.isValid())
            throw new ServiceException(ErrorCode.UnknownError);
        if (request.getUserName() != null) {
            // Einzelner Schüler
            exclusionService.addExclusion(
                    request.getUserName(),
                    request.getBlockId(),
                    request.getPeriodId(),
                    request.getReason()
            );
            return ResponseEntity.ok(Map.of("message", "Ausnahme hinzugefügt"));
        } else {
            // Gesamte Klassenstufe
            BatchExclusionResult result = exclusionService.addExclusionsForGrade(
                    request.getGrade(),
                    request.getBlockId(),
                    request.getPeriodId(),
                    request.getReason()
            );
            log.info("Ausnahmen für Klassenstufe {} hinzugefügt: {}",
                    request.getGrade(), result.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> removeExclusion(@PathVariable Long id) {
        try {
            exclusionService.removeExclusionById(id);
            log.info("Ausnahme {} entfernt", id);
            return ResponseEntity.ok(Map.of("message", "Ausnahme entfernt"));
        } catch (IllegalArgumentException e) {
            log.error("Fehler beim Entfernen der Ausnahme: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @GetMapping("/check")
    public ResponseEntity<Map<String, Boolean>> checkExclusion(
            @RequestParam String userName,
            @RequestParam Long blockId,
            @RequestParam Long periodId) {
        boolean excluded = exclusionService.isUserExcluded(userName, blockId, periodId);
        return ResponseEntity.ok(Map.of("excluded", excluded));
    }
}