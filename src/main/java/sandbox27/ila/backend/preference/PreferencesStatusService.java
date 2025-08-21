package sandbox27.ila.backend.preference;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.assignements.CourseUserAssignmentRepository;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.frontend.marshalling.LocalDateSerializer;
import sandbox27.ila.infrastructure.error.ErrorCode;
import sandbox27.ila.infrastructure.error.ServiceException;
import sandbox27.ila.infrastructure.security.AuthenticatedUser;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@Transactional
@RequestMapping("/preferences-status")
@RequiredArgsConstructor
public class PreferencesStatusService {

    public record PreferencesStatus(
            Double progress,
            List<String> categories,
            boolean isCourseSelectionComplete,
            boolean isCategoryDistributionOk,
            boolean readyToSubmit,
            boolean submitted,
            List<String> advices) {
    }

    final PeriodRepository periodRepository;
    final PreferenceRepository preferenceRepository;
    final BlockRepository blockRepository;
    final CourseUserAssignmentRepository courseUserAssignmentRepository;
    final PeriodUserPreferencesSubmitStatusRepository periodUserPreferencesSubmitStatusRepository;

    @GetMapping
    public PreferencesStatus getPreferencesStatus(@AuthenticatedUser User user) throws ServiceException {
        Period currentPeriod = periodRepository.findByCurrent(true).orElseThrow(() -> new ServiceException(ErrorCode.PeriodNotStartedYet));
        List<String> selectedCategories = getSelectedCategories(user, currentPeriod);
        double blocksDefined = getBlocksDefined(currentPeriod, user);
        long distinctCount = selectedCategories.stream()
                .distinct()
                .count();
        boolean courseSelectionComplete = selectedCategories.size() == 3;
        boolean categoryDistributionOk = distinctCount >= 2;
        List<String> advices = new ArrayList<>();
        if (blocksDefined < 1.0)
            advices.add("Bearbeite alle " + blockRepository.findAllByPeriod_idOrderByDayOfWeekAscStartTimeAsc(currentPeriod.getId()).size() + " Blöcke");
        if (!courseSelectionComplete)
            advices.add("Belege genau 3 Blöcke");
        if (!categoryDistributionOk)
            advices.add("Setze mindestens 2 verschiedenen Kategorien auf Platz 1");
        PeriodUserPreferencesSubmitStatus submitStatus = periodUserPreferencesSubmitStatusRepository.findByUserAndPeriod(user, currentPeriod).orElse(PeriodUserPreferencesSubmitStatus.builder().submitted(false).build());
        return new PreferencesStatus(getBlocksDefined(currentPeriod, user),
                selectedCategories,
                courseSelectionComplete,
                categoryDistributionOk,
                courseSelectionComplete && categoryDistributionOk && blocksDefined == 1.0,
                submitStatus.isSubmitted(),
                advices
        );
    }

    @PostMapping
    public ResponseEntity<Void> submitPreferences(@AuthenticatedUser User user) throws ServiceException {
        Period currentPeriod = periodRepository.findByCurrent(true).get();
        if (currentPeriod.getEndDate().isBefore(LocalDate.now()) || currentPeriod.getStartDate().isAfter(LocalDate.now()))
            throw new ServiceException(ErrorCode.PeriodNotEditable);
        PeriodUserPreferencesSubmitStatus currentStatus = periodUserPreferencesSubmitStatusRepository
                .findByUserAndPeriod(user, currentPeriod)
                .orElse(PeriodUserPreferencesSubmitStatus.builder()
                        .user(user)
                        .period(currentPeriod)
                        .submitted(true)
                        .build());
        currentStatus.setSubmitted(true);
        periodUserPreferencesSubmitStatusRepository.save(currentStatus);
        return ResponseEntity.ok().build();
    }


    private double getBlocksDefined(Period period, User user) {
        double totalBlocks = blockRepository.findAllByPeriod_idOrderByDayOfWeekAscStartTimeAsc(period.getId()).size();
        final Set<Block> definedBlocks = new HashSet<>();
        // fixed assignment
        courseUserAssignmentRepository.findByUserAndBlock_Period(user, period)
                .forEach(assignment -> {
                    definedBlocks.add(assignment.getBlock());
                });
        // preferences
        preferenceRepository.findByUserAndBlock_Period(user, period)
                .forEach(preference -> {
                    definedBlocks.add(preference.getBlock());
                });
        return definedBlocks.size() / totalBlocks;
    }


    private List<String> getSelectedCategories(User user, Period period) {
        List<String> selectedCategories = new ArrayList<>();
        // fixed assignment
        courseUserAssignmentRepository.findByUserAndBlock_Period(user, period)
                .forEach(assignment -> {
                    selectedCategories.addAll(assignment.getCourse().getCourseCategories().stream().map(cc -> cc.name()).collect(Collectors.toUnmodifiableList()));
                });
        // preferences
        preferenceRepository.findByUserAndBlock_Period(user, period)
                .stream()
                .filter(preference -> preference.getPreferenceIndex() == 0)
                .forEach(preference -> {
                    selectedCategories.addAll(preference.getCourse().getCourseCategories().stream().map(cc -> cc.name()).collect(Collectors.toUnmodifiableList()));
                });
        return selectedCategories;
    }
}
