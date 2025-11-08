package sandbox27.ila.backend.exclusion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserBlockExclusionService {

    private final UserBlockExclusionRepository exclusionRepository;
    private final UserRepository userRepository;
    private final BlockRepository blockRepository;
    private final PeriodRepository periodRepository;

    @Transactional
    public void addExclusion(String userName, Long blockId, Long periodId, String reason) {
        User user = userRepository.findById(userName)
                .orElseThrow(() -> new IllegalArgumentException("Schüler nicht gefunden: " + userName));
        Block block = blockRepository.findById(blockId)
                .orElseThrow(() -> new IllegalArgumentException("Block nicht gefunden: " + blockId));
        Period period = periodRepository.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Period nicht gefunden: " + periodId));

        if (exclusionRepository.existsByUserAndBlockAndPeriod(user, block, period)) {
            log.info("Ausnahme bereits vorhanden für User {} und Block {}", userName, blockId);
            return;
        }

        UserBlockExclusion exclusion = new UserBlockExclusion(user, block, period, reason);
        exclusionRepository.save(exclusion);
        log.info("Ausnahme hinzugefügt für User {} und Block {}", userName, blockId);
    }

    @Transactional
    public BatchExclusionResult addExclusionsForGrade(int grade, Long blockId, Long periodId, String reason) {
        Block block = blockRepository.findById(blockId)
                .orElseThrow(() -> new IllegalArgumentException("Block nicht gefunden: " + blockId));
        Period period = periodRepository.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Period nicht gefunden: " + periodId));

        List<User> students = userRepository.findAllByGrade(grade).stream()
                .filter(User::isIlaMember)
                .collect(Collectors.toList());

        if (students.isEmpty()) {
            throw new IllegalArgumentException("Keine iLA-Schüler in Klassenstufe " + grade + " gefunden");
        }

        int added = 0;
        int skipped = 0;

        for (User student : students) {
            if (!exclusionRepository.existsByUserAndBlockAndPeriod(student, block, period)) {
                UserBlockExclusion exclusion = new UserBlockExclusion(student, block, period, reason);
                exclusionRepository.save(exclusion);
                added++;
            } else {
                skipped++;
            }
        }

        log.info("Ausnahmen für Klassenstufe {} hinzugefügt: {} neu, {} übersprungen",
                grade, added, skipped);
        return BatchExclusionResult.success(added, skipped);
    }

    @Transactional
    public void removeExclusionById(Long id) {
        UserBlockExclusion exclusion = exclusionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ausnahme nicht gefunden: " + id));

        exclusionRepository.delete(exclusion);
        log.info("Ausnahme entfernt: ID {}, User {}, Block {}",
                id, exclusion.getUser().getUserName(), exclusion.getBlock().getId());
    }

    /**
     * Entfernt eine spezifische Ausnahme
     */
    @Transactional
    public void removeExclusion(String userName, Long blockId, Long periodId) {
        User user = userRepository.findById(userName)
                .orElseThrow(() -> new IllegalArgumentException("Schüler nicht gefunden: " + userName));
        Block block = blockRepository.findById(blockId)
                .orElseThrow(() -> new IllegalArgumentException("Block nicht gefunden: " + blockId));
        Period period = periodRepository.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Period nicht gefunden: " + periodId));

        exclusionRepository.deleteByUserAndBlockAndPeriod(user, block, period);
        log.info("Ausnahme entfernt für User {} und Block {}", userName, blockId);
    }

    /**
     * Gibt alle Ausnahmen für eine Period zurück
     */
    @Transactional(readOnly = true)
    public List<UserBlockExclusionDTO> getExclusionsForPeriod(Long periodId) {
        return exclusionRepository.findByPeriodIdWithDetails(periodId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Gibt alle ausgeschlossenen Block-IDs für einen User zurück
     */
    @Transactional(readOnly = true)
    public Set<Long> getExcludedBlockIds(String userName, Long periodId) {
        return exclusionRepository.findExcludedBlockIdsByUserAndPeriod(userName, periodId);
    }

    /**
     * Prüft ob ein User von einem Block ausgeschlossen ist
     */
    @Transactional(readOnly = true)
    public boolean isUserExcluded(String userName, Long blockId, Long periodId) {
        User user = userRepository.findById(userName).orElse(null);
        Block block = blockRepository.findById(blockId).orElse(null);
        Period period = periodRepository.findById(periodId).orElse(null);

        if (user == null || block == null || period == null) {
            return false;
        }

        return exclusionRepository.existsByUserAndBlockAndPeriod(user, block, period);
    }

    /**
     * Konvertiert Entity zu DTO
     */
    private UserBlockExclusionDTO toDTO(UserBlockExclusion exclusion) {
        return new UserBlockExclusionDTO(
                exclusion.getId(),
                exclusion.getUser().getUserName(),
                exclusion.getUser().getFirstName(),
                exclusion.getUser().getLastName(),
                exclusion.getUser().getGrade(),
                exclusion.getBlock().getId(),
                exclusion.getPeriod().getId(),
                exclusion.getReason(),
                exclusion.getCreatedAt()
        );
    }
}