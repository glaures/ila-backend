package sandbox27.ila.backend.preference;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.assignments.CourseUserAssignmentRepository;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.course.CourseBlockAssignment;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.course.CourseService;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.user.User;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;
import sandbox27.infrastructure.security.AuthenticatedUser;

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

    private final static int MIN_DIFFERENT_CATEGORIES = 3;

    /**
     * Ab dieser Klassenstufe entfällt die Kategorienvorgabe: Die Oberstufe wählt nach eigenen
     * Schwerpunkten, eine erzwungene Streuung über drei Kategorien ergibt dort keinen Sinn.
     */
    private final static int MIN_GRADE_WITHOUT_CATEGORY_RULE = 11;

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
    final CourseBlockAssignmentRepository courseBlockAssignmentRepository;
    final PeriodUserPreferencesSubmitStatusRepository periodUserPreferencesSubmitStatusRepository;
    final CourseService courseService;

    @GetMapping
    public PreferencesStatus getPreferencesStatus(@AuthenticatedUser User user) throws ServiceException {
        Period currentPeriod = periodRepository.findByCurrent(true).orElseThrow(() -> new ServiceException(ErrorCode.PeriodNotStartedYet));
        List<String> selectedCategories = getSelectedCategories(user, currentPeriod);
        BlockProgress blockProgress = getBlockProgress(currentPeriod, user);
        double blocksDefined = blockProgress.progress();
        long distinctCount = selectedCategories.stream()
                .distinct()
                .count();
        boolean courseSelectionComplete = true;//selectedCategories.size() == 3;
        boolean categoryDistributionOk = !isCategoryRuleApplicable(user) || distinctCount >= MIN_DIFFERENT_CATEGORIES;
        List<String> advices = new ArrayList<>();
        if (blocksDefined < 1.0)
            advices.add("Bearbeite alle " + blockProgress.relevantBlockIds().size() + " Blöcke");
        /*
        if (!courseSelectionComplete)
            advices.add("Belege genau 3 Blöcke");
         */
        if (!categoryDistributionOk)
            advices.add("Setze mindestens " + MIN_DIFFERENT_CATEGORIES + " verschiedenen Kategorien auf Platz 1");
        PeriodUserPreferencesSubmitStatus submitStatus = periodUserPreferencesSubmitStatusRepository.findByUserAndPeriod(user, currentPeriod).orElse(PeriodUserPreferencesSubmitStatus.builder().submitted(false).build());
        return new PreferencesStatus(blocksDefined,
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


    /**
     * Bearbeitungsstand über die Blöcke einer Phase.
     *
     * @param definedBlockIds  Blöcke, für die der Nutzer eine Zuweisung oder Präferenzen hat
     * @param relevantBlockIds Blöcke, die von ihm überhaupt bearbeitet werden können
     */
    record BlockProgress(Set<Long> definedBlockIds, Set<Long> relevantBlockIds) {

        double progress() {
            if (relevantBlockIds.isEmpty())
                return 1.0;
            return Math.min((double) definedBlockIds.size() / relevantBlockIds.size(), 1.0);
        }
    }

    /**
     * Blöcke ohne für den Nutzer wählbare Kurse zählen nicht mit: Gibt es in einem Block etwa nur
     * Kurse für andere Klassenstufen, kann der Schüler dort keine Präferenzen setzen und wäre sonst
     * dauerhaft von der Abgabe ausgesperrt. Maßgeblich ist dieselbe Auswahllogik wie in der
     * Kursliste ({@link CourseService#getSelectableCourses}).
     */
    BlockProgress getBlockProgress(Period period, User user) {
        final Set<Long> definedBlockIds = new HashSet<>();
        // fixed assignment
        courseUserAssignmentRepository.findByUserAndBlock_Period(user, period)
                .forEach(assignment -> {
                    Block assignedBlock = assignment.getBlock();
                    // da der andere Block an diesem Tag keine Präferenzen bekommt, werden beide
                    // Blöcke des Tages hinzugefügt
                    blockRepository.findByPeriod_IdAndDayOfWeek(period.getId(), assignedBlock.getDayOfWeek())
                            .forEach(block -> definedBlockIds.add(block.getId()));
                });
        // preferences
        preferenceRepository.findByUserAndBlock_Period(user, period)
                .forEach(preference -> {
                    Block preferenceBlock = courseBlockAssignmentRepository.findByCourse(preference.getCourse()).get().getBlock();
                    definedBlockIds.add(preferenceBlock.getId());
                });

        // Bereits bearbeitete Blöcke bleiben relevant, auch wenn dort inzwischen nichts mehr
        // wählbar ist – sonst würde eine Kursänderung den Fortschritt über 100% treiben.
        final Set<Long> relevantBlockIds = new HashSet<>(definedBlockIds);
        blockRepository.findAllByPeriod_idOrderByDayOfWeekAscStartTimeAsc(period.getId())
                .forEach(block -> {
                    if (!courseService.getSelectableCourses(block.getId(), user).isEmpty())
                        relevantBlockIds.add(block.getId());
                });

        return new BlockProgress(definedBlockIds, relevantBlockIds);
    }


    /**
     * Ob die Vorgabe, mehrere verschiedene Kategorien auf Platz 1 zu setzen, für diesen Nutzer
     * gilt. Ab der Oberstufe entfällt sie.
     */
    boolean isCategoryRuleApplicable(User user) {
        return user.getGrade() < MIN_GRADE_WITHOUT_CATEGORY_RULE;
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
