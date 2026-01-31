package sandbox27.ila.backend.exchange;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sandbox27.ila.backend.assignments.CourseUserAssignment;
import sandbox27.ila.backend.assignments.CourseUserAssignmentRepository;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseBlockAssignment;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.course.CourseCategory;
import sandbox27.ila.backend.exclusion.UserBlockExclusionService;
import sandbox27.ila.backend.user.User;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;

import java.time.DayOfWeek;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseEligibilityService {

    private static final int COURSES_PER_STUDENT = 3;
    private static final int MIN_CATEGORIES = 2;

    private final CourseUserAssignmentRepository assignmentRepository;
    private final CourseBlockAssignmentRepository courseBlockAssignmentRepository;
    private final UserBlockExclusionService userBlockExclusionService;

    /**
     * Ermittelt den Block eines Kurses über CourseBlockAssignment
     */
    public Block getBlockForCourse(Course course) {
        return courseBlockAssignmentRepository.findByCourse(course)
                .map(CourseBlockAssignment::getBlock)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound,
                        "Kein Block für Kurs " + course.getName() + " gefunden"));
    }

    /**
     * Prüft, ob ein Schüler einen bestimmten Kurs belegen darf.
     * Berücksichtigt alle aktuellen Zuweisungen des Schülers.
     */
    public EligibilityResult checkEligibility(User student, Course course, Long periodId) {
        List<CourseUserAssignment> currentAssignments =
                assignmentRepository.findByUserAndCourse_Period_Id(student, periodId);

        return checkEligibilityWithAssignments(student, course, currentAssignments, periodId);
    }

    /**
     * Prüft die Berechtigung mit einer expliziten Liste von Zuweisungen.
     * Nützlich für Simulationen (z.B. "was wäre wenn ich diesen Kurs abgebe?")
     */
    public EligibilityResult checkEligibilityWithAssignments(
            User student,
            Course course,
            List<CourseUserAssignment> currentAssignments,
            Long periodId) {

        // Block über CourseBlockAssignment ermitteln
        Block targetBlock = getBlockForCourse(course);

        // === HARTE AUSSCHLÜSSE (Kurs wird nicht in der Liste angezeigt) ===

        // 1. Kurs ist nur für manuelle Zuweisung
        if (course.isManualAssignmentOnly()) {
            return EligibilityResult.excluded("Kurs ist nur für manuelle Zuweisung vorgesehen");
        }

        // 2. Kurs ist ein Platzhalter
        if (course.isPlaceholder()) {
            return EligibilityResult.excluded("Kurs ist ein Platzhalter");
        }

        // 3. Block für User gesperrt?
        Set<Long> excludedBlocks = userBlockExclusionService.getExcludedBlockIds(
                student.getUserName(), periodId);
        if (excludedBlocks.contains(targetBlock.getId())) {
            return EligibilityResult.excluded("Block ist für diesen Schüler gesperrt");
        }

        // 4. Klassenstufe passt?
        if (!course.getGrades().isEmpty() && !course.getGrades().contains(student.getGrade())) {
            return EligibilityResult.excluded("Klassenstufe " + student.getGrade() + " nicht zugelassen");
        }

        // 5. Geschlecht nicht ausgeschlossen?
        if (course.getExcludedGenders().contains(student.getGender())) {
            return EligibilityResult.excluded("Geschlecht ist für diesen Kurs ausgeschlossen");
        }

        // === WEICHE EINSCHRÄNKUNGEN (Kurs wird angezeigt, aber als "nicht ideal" markiert) ===

        // 6. Tageskonflikt? (kein zweiter Kurs am selben Tag)
        Set<DayOfWeek> assignedDays = currentAssignments.stream()
                .map(a -> a.getBlock().getDayOfWeek())
                .collect(Collectors.toSet());

        if (assignedDays.contains(targetBlock.getDayOfWeek())) {
            return EligibilityResult.ineligible("Tageskonflikt");
        }

        // 7. Kategorien-Regel: Nach Zuweisung mind. 2 Kategorien möglich?
        Set<CourseCategory> currentCategories = currentAssignments.stream()
                .flatMap(a -> a.getCourse().getCourseCategories().stream())
                .collect(Collectors.toSet());

        Set<CourseCategory> newCategories = new HashSet<>(currentCategories);
        newCategories.addAll(course.getCourseCategories());

        int assignmentsAfter = currentAssignments.size() + 1;
        int remainingSlots = COURSES_PER_STUDENT - assignmentsAfter;

        // Wenn das der letzte Kurs wäre und wir nicht genug Kategorien haben
        if (remainingSlots == 0 && newCategories.size() < MIN_CATEGORIES) {
            return EligibilityResult.ineligible("Mindestens " + MIN_CATEGORIES +
                    " verschiedene Kategorien erforderlich");
        }

        // 8. Schüler hat bereits max. Kurse?
        if (currentAssignments.size() >= COURSES_PER_STUDENT) {
            return EligibilityResult.ineligible("Schüler hat bereits " + COURSES_PER_STUDENT + " Kurse");
        }

        // 9. Kurs voll? (Warnung, aber trotzdem wählbar für Wechselrunde)
        int currentAttendees = assignmentRepository.countByCourseAndBlock(course, targetBlock);
        if (currentAttendees >= course.getMaxAttendees()) {
            return EligibilityResult.eligibleWithWarning("Kurs ist aktuell voll (" + currentAttendees + "/" + course.getMaxAttendees() + ")");
        }

        return EligibilityResult.eligible();
    }

    /**
     * Prüft, ob ein Wechsel von einem Kurs zu einem anderen möglich ist.
     * Simuliert den Zustand nach Abgabe des alten Kurses.
     */
    public EligibilityResult checkExchangeEligibility(
            User student,
            CourseUserAssignment assignmentToGiveUp,
            Course desiredCourse,
            Long periodId) {

        // Lade aktuelle Zuweisungen
        List<CourseUserAssignment> currentAssignments =
                assignmentRepository.findByUserAndCourse_Period_Id(student, periodId);

        // Simuliere: Entferne die abzugebende Zuweisung
        List<CourseUserAssignment> simulatedAssignments = currentAssignments.stream()
                .filter(a -> !a.getId().equals(assignmentToGiveUp.getId()))
                .collect(Collectors.toList());

        // Prüfe mit simuliertem Zustand
        return checkEligibilityWithAssignments(student, desiredCourse, simulatedAssignments, periodId);
    }

    /**
     * Gibt die Anzahl freier Plätze in einem Kurs zurück
     */
    public int getAvailableSpots(Course course) {
        Block block = getBlockForCourse(course);
        int currentAttendees = assignmentRepository.countByCourseAndBlock(course, block);
        return Math.max(0, course.getMaxAttendees() - currentAttendees);
    }

    /**
     * Prüft, ob ein Kurs noch freie Plätze hat
     */
    public boolean hasAvailableSpots(Course course) {
        return getAvailableSpots(course) > 0;
    }
}