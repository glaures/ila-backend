package sandbox27.ila.backend.assignementprocess;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sandbox27.ila.backend.assignements.CourseUserAssignment;
import sandbox27.ila.backend.assignements.CourseUserAssignmentRepository;
import sandbox27.ila.backend.assignementprocess.dtos.AssignmentInputDTO;
import sandbox27.ila.backend.assignementprocess.dtos.CourseUserAssignmentDTO;
import sandbox27.ila.backend.assignementprocess.dtos.PreferenceDTO;
import sandbox27.ila.backend.assignementprocess.dtos.UserDTO;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseBlockAssignment;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.preference.Preference;
import sandbox27.ila.backend.preference.PreferenceRepository;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;

import java.util.*;

import static sandbox27.ila.backend.assignementprocess.AssignmentAlgorithmService.safeList;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssignmentServiceRunner {

    final AssignmentAlgorithmService assignmentAlgorithmServiceV2;
    final AssignmentInputMapperService assignmentInputMapperService;
    final PeriodRepository periodRepository;
    final UserRepository userRepository;
    final BlockRepository blockRepository;
    final CourseRepository courseRepository;
    final CourseBlockAssignmentRepository courseBlockAssignmentRepository;
    final PreferenceRepository preferenceRepository;
    final CourseUserAssignmentRepository courseUserAssignmentRepository;

    public List<CourseUserAssignmentDTO> runAssignmentProcess(long periodId) throws ServiceException {
        Period currentPeriod = periodRepository.findById(periodId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));
        List<User> users = userRepository.findAll().stream().filter(u -> u.getGrade() > 0 && !"testschueler".equals(u.getUserName())).toList();
        List<Block> blocks = blockRepository.findAllByPeriod_idOrderByDayOfWeekAscStartTimeAsc(currentPeriod.getId());
        List<Course> courses = courseRepository.findAllByPeriod(currentPeriod);
        List<CourseBlockAssignment> courseBlockAssignments = courseBlockAssignmentRepository.findAllByPeriodId(currentPeriod.getId());
        List<CourseUserAssignment> predefinedAssignments = courseUserAssignmentRepository.findByCourse_Period(currentPeriod);
        List<Preference> preferences = preferenceRepository.findAllByBlock_Period(currentPeriod);
        AssignmentInputDTO input = assignmentInputMapperService.buildInput(users, blocks, courses, courseBlockAssignments, preferences, predefinedAssignments);
        input = validateAndFixThreeBlocks(input);
        List<CourseUserAssignmentDTO> result= assignmentAlgorithmServiceV2.assignCourses(input);
        return result;
    }

    /**
     * Validiert und fixt pro Schüler die Menge der belegten/angestrebten Blöcke auf genau 3.
     * Definition:
     *   UNION(Blocks aus vorbelegten KURS-Zuweisungen, Blocks aus KURS-Präferenzen) == 3
     *   (Pausen werden ignoriert)
     *
     * Fix:
     *   - Wenn > 3: zufällig Blöcke aus den Präferenz-Blöcken (nicht aus Vorbelegungen) entfernen,
     *               indem ALLE Präferenzen des Schülers in diesen (user, block) gelöscht werden.
     *   - Wenn < 3: nur Warnung.
     *
     * @return das (ggf. modifizierte) input zurück (Präferenzliste kann gekürzt sein).
     */
    private AssignmentInputDTO validateAndFixThreeBlocks(final AssignmentInputDTO input) {
        if (input == null) return null;

        final Random rnd = AssignmentAlgorithmService.rng(); // nutzt ggf. deinen Seed
        final List<PreferenceDTO> prefs = new ArrayList<>(safeList(input.getPreferences()));

        // Vorbelegte KURS-Blöcke pro User (Pausen ignorieren)
        final Map<String, Set<Long>> predefBlocksByUser = new HashMap<>();
        for (CourseUserAssignmentDTO a : safeList(input.getPredefinedAssignments())) {
            if (a == null) continue;
            if (a.isPause()) continue;
            if (a.getCourseId() == null) continue;
            predefBlocksByUser.computeIfAbsent(a.getUserName(), k -> new LinkedHashSet<>()).add(a.getBlockId());
        }

        // Präferenz-Blöcke pro User (nur KURS-Präferenzen, Pausen ignorieren)
        final Map<String, Set<Long>> prefBlocksByUser = new HashMap<>();
        for (PreferenceDTO p : prefs) {
            if (p == null) continue;
            if (isPausePreference(p)) continue;
            prefBlocksByUser.computeIfAbsent(p.getUserName(), k -> new LinkedHashSet<>()).add(p.getBlockId());
        }

        // Alle relevanten User (aus Users, Prefs, Vorbelegungen)
        final Set<String> allUsers = new LinkedHashSet<>();
        for (UserDTO u : safeList(input.getUsers())) allUsers.add(u.getUserName());
        allUsers.addAll(predefBlocksByUser.keySet());
        allUsers.addAll(prefBlocksByUser.keySet());

        for (String user : allUsers) {
            final Set<Long> predef = new LinkedHashSet<>(predefBlocksByUser.getOrDefault(user, Collections.emptySet()));
            final Set<Long> pref   = new LinkedHashSet<>(prefBlocksByUser.getOrDefault(user, Collections.emptySet()));

            final Set<Long> combined = new LinkedHashSet<>(predef);
            combined.addAll(pref);

            final int size = combined.size();
            if (size == 3) continue;

            if (size < 3) {
                log.warn("validateAndFixThreeBlocks: {} hat nur {} Blöcke (vorbelegt={}, prefs={}) – keine automatische Ergänzung.",
                        user, size, predef.size(), pref.size());
                continue;
            }

            // size > 3: reduzieren
            final int toRemove = size - 3;

            // Nur Präferenz-Blöcke dürfen entfernt werden (Vorbelegungen sind fix)
            final List<Long> removablePrefBlocks = new ArrayList<>(pref);
            removablePrefBlocks.removeAll(predef); // schütze vorbelegte Blöcke

            if (predef.size() > 3) {
                // Hier können wir nicht "fixen", weil schon die Vorbelegungen zu viele Blöcke belegen.
                log.warn("validateAndFixThreeBlocks: {} hat bereits {} vorbelegte Kurs-Blöcke (>3). Präferenzen werden nicht verändert.",
                        user, predef.size());
                continue;
            }

            if (removablePrefBlocks.isEmpty()) {
                // Nichts zum Entfernen vorhanden (alle 3+ Blöcke stammen aus Vorbelegungen) – schon oben geloggt, hier nur Safety.
                continue;
            }

            Collections.shuffle(removablePrefBlocks, rnd);
            final List<Long> blocksToDrop = removablePrefBlocks.subList(0, Math.min(toRemove, removablePrefBlocks.size()));

            // Entferne alle Präferenzen des Users in diesen Blöcken
            int before = prefs.size();
            prefs.removeIf(p -> user.equals(p.getUserName()) && blocksToDrop.contains(p.getBlockId()));
            int removed = before - prefs.size();

            log.info("validateAndFixThreeBlocks: {} hatte {} Blöcke, entferne {} Präferenz-Block/Blöcke {} ({} Präferenzen gelöscht), jetzt 3.",
                    user, size, blocksToDrop.size(), blocksToDrop, removed);
        }

        // Schreibe gefixte Präferenzen zurück
        try {
            input.setPreferences(prefs);
        } catch (Exception e) {
            // Falls kein Setter vorhanden ist, zumindest warnen.
            log.warn("validateAndFixThreeBlocks: Konnte Preferences nicht zurückschreiben (kein Setter?). Änderungen nur lokal wirksam.");
        }

        return input;
    }

    /** Kurs-Pause-Präferenz? (negativer Index ODER courseId == 'PAUSE') */
    private static boolean isPausePreference(PreferenceDTO p) {
        if (p == null) return false;
        if (p.getPreferenceIndex() < 0) return true;
        final String cid = p.getCourseId();
        return AssignmentAlgorithmService.PAUSE_COURSE_ID_MARKER.equalsIgnoreCase(cid);
    }
}
