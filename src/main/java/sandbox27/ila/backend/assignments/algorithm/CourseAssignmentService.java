package sandbox27.ila.backend.assignments.algorithm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseCategory;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.course.CourseBlockAssignment;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.assignments.CourseUserAssignment;
import sandbox27.ila.backend.assignments.CourseUserAssignmentRepository;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.preference.Preference;
import sandbox27.ila.backend.preference.PreferenceRepository;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;
import sandbox27.ila.backend.exclusion.UserBlockExclusionService;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;

import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseAssignmentService {

    private final PeriodRepository periodRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final BlockRepository blockRepository;
    private final PreferenceRepository preferenceRepository;
    private final CourseUserAssignmentRepository courseUserAssignmentRepository;
    private final CourseBlockAssignmentRepository courseBlockAssignmentRepository;
    private final UserBlockExclusionService userBlockExclusionService;
    private final AssignmentResultRepository assignmentResultRepository;

    private static final int COURSES_PER_STUDENT = 3;
    private static final int MIN_CATEGORIES = 2;
    private static final int MAX_ITERATIONS = 50;
    private static final int SWAP_ATTEMPTS = 1000;

    List<AssignmentResult> getAllAssignmentResultsForPeriod(long periodId) {
        return assignmentResultRepository.findByPeriod_IdOrderByExecutedAtDesc(periodId);
    }

    @Transactional
    public AssignmentResult assignCourses(Long periodId) {
        long startTime = System.currentTimeMillis();

        Period period = periodRepository.findById(periodId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound, "Period", periodId));

        // Load all necessary data
        List<User> students = userRepository.findAll().stream()
                .filter(User::isIlaMember)
                .filter(u -> u.getRoles().stream().anyMatch(r -> r.name().contains("STUDENT")))
                .filter(u -> u.getGrade() > 0)
                .collect(Collectors.toList());

        List<Course> courses = courseRepository.findAllByPeriod(period).stream()
                .filter(c -> !c.isManualAssignmentOnly())
                .filter(c -> !c.isPlaceholder())
                .collect(Collectors.toList());

        List<Block> blocks = blockRepository.findByPeriod(period);

        // WICHTIG: Erstelle Course -> Block Mapping aus CourseBlockAssignment
        // Dies ist die EINZIGE Quelle der Wahrheit für den Block eines Kurses!
        Map<Long, Block> courseToBlock = new HashMap<>();
        List<CourseBlockAssignment> courseBlockAssignments = courseBlockAssignmentRepository.findAllByPeriodId(periodId);
        for (CourseBlockAssignment cba : courseBlockAssignments) {
            courseToBlock.put(cba.getCourse().getId(), cba.getBlock());
        }
        log.info("Loaded {} course-block assignments", courseToBlock.size());

        Map<String, List<Preference>> userPreferences = preferenceRepository
                .findAllByBlock_Period(period)
                .stream()
                .collect(Collectors.groupingBy(p -> p.getUser().getUserName()));

        // Load user block exclusions
        Map<String, Set<Long>> userBlockExclusions = new HashMap<>();
        for (User student : students) {
            Set<Long> excludedBlocks = userBlockExclusionService.getExcludedBlockIds(student.getUserName(), periodId);
            if (!excludedBlocks.isEmpty()) {
                userBlockExclusions.put(student.getUserName(), excludedBlocks);
            }
        }
        log.info("Loaded {} user block exclusions", userBlockExclusions.size());

        // Clear existing assignments that are not preset
        List<CourseUserAssignment> existingAssignments = courseUserAssignmentRepository
                .findByCourse_Period(period);

        // Separate preset and non-preset assignments
        List<CourseUserAssignment> presetAssignments = existingAssignments.stream()
                .filter(CourseUserAssignment::isPreset)
                .collect(Collectors.toList());

        List<CourseUserAssignment> toDelete = existingAssignments.stream()
                .filter(a -> !a.isPreset())
                .collect(Collectors.toList());
        courseUserAssignmentRepository.deleteAll(toDelete);

        // Initialize assignment state - jetzt mit courseToBlock Map!
        AssignmentState state = new AssignmentState(students, courses, blocks, userPreferences, userBlockExclusions, courseToBlock);

        // Load preset assignments into state
        log.info("Loading {} preset assignments into state", presetAssignments.size());
        for (CourseUserAssignment preset : presetAssignments) {
            User student = preset.getUser();
            // Priority -1 indicates a preset assignment (not from preferences)
            state.assign(student, preset.getBlock(), preset.getCourse(), -1);
        }

        // Log students with constraints
        logStudentsWithConstraints(state);

        // Phase 1: Greedy assignment with fairness
        log.info("Phase 1: Greedy assignment with fairness balancing");
        greedyAssignmentWithFairness(state);

        // Phase 2: Local optimization through swaps
        log.info("Phase 2: Local optimization through swaps");
        localOptimization(state);

        // Phase 3: Assign students without preferences
        log.info("Phase 3: Assigning students without preferences");
        int studentsWithoutPreferences = assignStudentsWithoutPreferences(state);

        // Phase 4: Fill incomplete assignments (relaxed category constraint)
        log.info("Phase 4: Filling incomplete assignments (relaxed category constraint)");
        int filledInPhase4 = fillIncompleteAssignmentsRelaxed(state);
        if (filledInPhase4 > 0) {
            log.info("Phase 4 completed: {} additional assignments made", filledInPhase4);
        }

        // Save assignments to database
        log.info("Saving assignments to database");
        saveAssignments(state, period);

        // Generate statistics
        AssignmentResult result = generateStatistics(state, studentsWithoutPreferences);

        // Calculate execution duration
        long executionDuration = System.currentTimeMillis() - startTime;
        result.setExecutionDurationMs(executionDuration);
        result.setPeriod(period);

        log.info("Assignment completed: {} students assigned, avg priority: {}, duration: {}ms",
                result.getAssignedStudents(), result.getAveragePriority(), executionDuration);

        return assignmentResultRepository.save(result);
    }

    /**
     * Loggt Schüler mit Einschränkungen (Block-Exclusions oder Presets) zur Transparenz.
     */
    private void logStudentsWithConstraints(AssignmentState state) {
        List<User> constrainedStudents = state.students.stream()
                .filter(s -> state.getExclusionCount(s) > 0 || state.getAssignmentCount(s) > 0)
                .sorted(Comparator.comparingInt(state::getRemainingSlots))
                .collect(Collectors.toList());

        if (!constrainedStudents.isEmpty()) {
            log.info("=== Schüler mit Einschränkungen (werden bevorzugt behandelt) ===");
            for (User student : constrainedStudents) {
                int exclusions = state.getExclusionCount(student);
                int presets = state.getAssignmentCount(student);
                int remainingSlots = state.getRemainingSlots(student);
                int availableBlocks = state.getAvailableBlocks(student).size();

                log.info("  {} (Klasse {}): {} Exclusions, {} Presets, {} verbleibende Slots, {} verfügbare Blöcke",
                        student.getUserName(),
                        student.getGrade(),
                        exclusions,
                        presets,
                        remainingSlots,
                        availableBlocks);
            }
        }
    }

    private void greedyAssignmentWithFairness(AssignmentState state) {
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            // Sort students using "Most Constrained Variable First" principle
            List<User> sortedStudents = new ArrayList<>(state.students);
            sortedStudents.sort(
                    Comparator.comparingInt(state::getRemainingSlots)
                            .thenComparingInt(s -> state.getAvailableBlocks(s).size())
                            .thenComparing(Comparator.comparingDouble(state::getFairnessScore).reversed())
            );

            boolean anyAssignment = false;

            for (User student : sortedStudents) {
                if (state.getAssignmentCount(student) >= COURSES_PER_STUDENT) {
                    continue;
                }

                Assignment bestAssignment = findBestAssignment(student, state);

                if (bestAssignment != null) {
                    state.assign(student, bestAssignment.block, bestAssignment.course, bestAssignment.priority);
                    anyAssignment = true;

                    if (state.getExclusionCount(student) > 0) {
                        log.debug("Constrained student {} assigned to {} (priority {}), now has {}/{} courses",
                                student.getUserName(),
                                bestAssignment.course.getName(),
                                bestAssignment.priority,
                                state.getAssignmentCount(student),
                                COURSES_PER_STUDENT);
                    }
                }
            }

            if (!anyAssignment) {
                log.info("No more assignments possible after iteration {}", iteration);
                break;
            }
        }
    }

    private Assignment findBestAssignment(User student, AssignmentState state) {
        List<Block> availableBlocks = state.getAvailableBlocks(student);
        Assignment bestAssignment = null;
        int bestPriority = Integer.MAX_VALUE;

        for (Block block : availableBlocks) {
            // Hole Präferenzen für diesen Block (basierend auf CourseBlockAssignment, nicht Preference.block!)
            List<Preference> prefs = state.getPreferencesForBlock(student, block);

            for (Preference pref : prefs) {
                Course course = pref.getCourse();

                if (isValidAssignment(student, block, course, state)) {
                    if (pref.getPreferenceIndex() < bestPriority) {
                        bestPriority = pref.getPreferenceIndex();
                        bestAssignment = new Assignment(block, course, pref.getPreferenceIndex());

                        if (bestPriority == 0) {
                            return bestAssignment;
                        }
                    }
                }
            }
        }

        return bestAssignment;
    }

    private boolean isValidAssignment(User student, Block block, Course course, AssignmentState state) {
        return isValidAssignment(student, block, course, state, true);
    }

    private boolean isValidAssignment(User student, Block block, Course course,
                                      AssignmentState state, boolean strictCategoryCheck) {
        if (state.isBlockExcludedForUser(student, block)) {
            return false;
        }

        if (state.getCourseAttendees(course, block) >= course.getMaxAttendees()) {
            return false;
        }

        if (!course.getGrades().isEmpty() && !course.getGrades().contains(student.getGrade())) {
            return false;
        }

        if (course.getExcludedGenders().contains(student.getGender())) {
            return false;
        }

        Set<DayOfWeek> assignedDays = state.getAssignedDays(student);
        if (assignedDays.contains(block.getDayOfWeek())) {
            return false;
        }

        if (strictCategoryCheck) {
            Set<CourseCategory> currentCategories = state.getAssignedCategories(student);
            Set<CourseCategory> newCategories = new HashSet<>(currentCategories);
            newCategories.addAll(course.getCourseCategories());

            int remainingSlots = COURSES_PER_STUDENT - state.getAssignmentCount(student) - 1;

            if (remainingSlots == 0 && newCategories.size() < MIN_CATEGORIES) {
                return false;
            }
        }

        return true;
    }

    private void localOptimization(AssignmentState state) {
        Random random = new Random();

        for (int attempt = 0; attempt < SWAP_ATTEMPTS; attempt++) {
            List<User> completeStudents = state.students.stream()
                    .filter(s -> state.getAssignmentCount(s) == COURSES_PER_STUDENT)
                    .collect(Collectors.toList());

            if (completeStudents.size() < 2) {
                break;
            }

            User student1 = completeStudents.get(random.nextInt(completeStudents.size()));
            User student2 = completeStudents.get(random.nextInt(completeStudents.size()));

            if (student1.equals(student2)) {
                continue;
            }

            trySwap(student1, student2, state);
        }
    }

    private void trySwap(User student1, User student2, AssignmentState state) {
        List<StudentAssignment> assignments1 = new ArrayList<>(state.getAssignments(student1));
        List<StudentAssignment> assignments2 = new ArrayList<>(state.getAssignments(student2));

        for (StudentAssignment a1 : assignments1) {
            for (StudentAssignment a2 : assignments2) {
                if (!a1.block.equals(a2.block)) {
                    continue;
                }

                double currentSatisfaction = state.getFairnessScore(student1) + state.getFairnessScore(student2);

                state.unassign(student1, a1.block);
                state.unassign(student2, a2.block);

                boolean swap1Valid = isValidAssignment(student1, a2.block, a2.course, state);
                boolean swap2Valid = isValidAssignment(student2, a1.block, a1.course, state);

                if (swap1Valid && swap2Valid) {
                    int priority1 = state.getPriorityForCourse(student1, a2.course);
                    int priority2 = state.getPriorityForCourse(student2, a1.course);

                    state.assign(student1, a2.block, a2.course, priority1);
                    state.assign(student2, a1.block, a1.course, priority2);

                    double newSatisfaction = state.getFairnessScore(student1) + state.getFairnessScore(student2);

                    if (newSatisfaction < currentSatisfaction) {
                        log.debug("Successful swap between {} and {}", student1.getUserName(), student2.getUserName());
                        return;
                    } else {
                        state.unassign(student1, a2.block);
                        state.unassign(student2, a1.block);
                        state.assign(student1, a1.block, a1.course, a1.priority);
                        state.assign(student2, a2.block, a2.course, a2.priority);
                    }
                } else {
                    state.assign(student1, a1.block, a1.course, a1.priority);
                    state.assign(student2, a2.block, a2.course, a2.priority);
                }
            }
        }
    }

    private int assignStudentsWithoutPreferences(AssignmentState state) {
        List<User> studentsWithoutPreferences = state.students.stream()
                .filter(s -> state.getAssignmentCount(s) == 0)
                .collect(Collectors.toList());

        log.info("Found {} students without preferences", studentsWithoutPreferences.size());

        if (studentsWithoutPreferences.isEmpty()) {
            return 0;
        }

        studentsWithoutPreferences.sort(
                Comparator.comparingInt(state::getExclusionCount).reversed()
                        .thenComparingInt(s -> state.getAvailableBlocks(s).size())
        );

        for (User student : studentsWithoutPreferences) {
            List<Block> availableBlocks = state.getAvailableBlocks(student);

            for (Block block : availableBlocks) {
                if (state.getAssignmentCount(student) >= COURSES_PER_STUDENT) {
                    break;
                }

                Course bestCourse = findMostPopularAvailableCourse(student, block, state);

                if (bestCourse != null) {
                    state.assign(student, block, bestCourse, 999);
                    log.debug("Assigned {} to course {} (no preference)",
                            student.getUserName(), bestCourse.getName());
                }
            }

            if (state.getAssignmentCount(student) < COURSES_PER_STUDENT) {
                log.warn("Student {} without preferences could only be assigned {} courses",
                        student.getUserName(), state.getAssignmentCount(student));
            }
        }

        return studentsWithoutPreferences.size();
    }

    private int fillIncompleteAssignmentsRelaxed(AssignmentState state) {
        int additionalAssignments = 0;

        List<User> incompleteStudents = state.students.stream()
                .filter(s -> {
                    int count = state.getAssignmentCount(s);
                    return count > 0 && count < COURSES_PER_STUDENT;
                })
                .collect(Collectors.toList());

        log.info("Found {} students with incomplete assignments", incompleteStudents.size());

        incompleteStudents.sort(
                Comparator.comparingInt(state::getExclusionCount).reversed()
                        .thenComparingInt(s -> state.getAvailableBlocks(s).size())
        );

        for (User student : incompleteStudents) {
            while (state.getAssignmentCount(student) < COURSES_PER_STUDENT) {
                Assignment bestAssignment = findBestAssignmentRelaxed(student, state);

                if (bestAssignment == null) {
                    log.warn("Schüler {} (Klasse {}) konnte nicht vollständig zugewiesen werden: {}/{} Kurse. " +
                                    "Verfügbare Blöcke: {}, Exclusions: {}",
                            student.getUserName(),
                            student.getGrade(),
                            state.getAssignmentCount(student),
                            COURSES_PER_STUDENT,
                            state.getAvailableBlocks(student).stream()
                                    .map(b -> b.getDayOfWeek().toString() + " " + b.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")))
                                    .collect(Collectors.joining(", ")),
                            state.getExclusionCount(student));
                    break;
                }

                state.assign(student, bestAssignment.block, bestAssignment.course, bestAssignment.priority);
                additionalAssignments++;

                Set<CourseCategory> categories = state.getAssignedCategories(student);
                log.info("Phase 4: Assigned {} to {} (relaxed category check). Categories: {}",
                        student.getUserName(),
                        bestAssignment.course.getName(),
                        categories);
            }
        }

        return additionalAssignments;
    }

    private Assignment findBestAssignmentRelaxed(User student, AssignmentState state) {
        List<Block> availableBlocks = state.getAvailableBlocks(student);
        Assignment bestPreferenceAssignment = null;
        int bestPriority = Integer.MAX_VALUE;

        // Phase 1: Suche nach Präferenzen über alle Blöcke
        for (Block block : availableBlocks) {
            List<Preference> prefs = state.getPreferencesForBlock(student, block);

            for (Preference pref : prefs) {
                Course course = pref.getCourse();

                if (isValidAssignment(student, block, course, state, false)) {
                    if (pref.getPreferenceIndex() < bestPriority) {
                        bestPriority = pref.getPreferenceIndex();
                        bestPreferenceAssignment = new Assignment(block, course, pref.getPreferenceIndex());

                        if (bestPriority == 0) {
                            return bestPreferenceAssignment;
                        }
                    }
                }
            }
        }

        if (bestPreferenceAssignment != null) {
            return bestPreferenceAssignment;
        }

        // Phase 2: Fallback - suche nach IRGENDEINEM verfügbaren Kurs in ALLEN Blöcken
        for (Block block : availableBlocks) {
            Course anyAvailable = findAnyAvailableCourse(student, block, state);
            if (anyAvailable != null) {
                return new Assignment(block, anyAvailable, 999);
            } else {
                logWhyNoCourseFound(student, block, state);
            }
        }

        return null;
    }

    private void logWhyNoCourseFound(User student, Block block, AssignmentState state) {
        List<Course> coursesInBlock = getCoursesInBlock(block, state);

        if (coursesInBlock.isEmpty()) {
            log.warn("  Block {} ({}): Keine Kurse diesem Block zugewiesen!",
                    block.getId(), block.getDayOfWeek());
            return;
        }

        log.warn("  Block {} ({}) - Analyse warum kein Kurs passt:", block.getId(), block.getDayOfWeek());

        for (Course course : coursesInBlock) {
            StringBuilder reasons = new StringBuilder();

            if (course.isManualAssignmentOnly()) {
                reasons.append("manualAssignmentOnly, ");
            }
            if (course.isPlaceholder()) {
                reasons.append("placeholder, ");
            }
            if (state.isBlockExcludedForUser(student, block)) {
                reasons.append("userExcluded, ");
            }
            if (state.getCourseAttendees(course, block) >= course.getMaxAttendees()) {
                reasons.append("voll (" + state.getCourseAttendees(course, block) + "/" + course.getMaxAttendees() + "), ");
            }
            if (!course.getGrades().isEmpty() && !course.getGrades().contains(student.getGrade())) {
                reasons.append("falsche Klasse (erlaubt: " + course.getGrades() + ", Schüler: " + student.getGrade() + "), ");
            }
            if (course.getExcludedGenders().contains(student.getGender())) {
                reasons.append("Geschlecht ausgeschlossen, ");
            }

            if (reasons.length() > 0) {
                log.warn("    - {} (ID {}): {}", course.getName(), course.getId(),
                        reasons.substring(0, reasons.length() - 2));
            } else {
                log.warn("    - {} (ID {}): SOLLTE PASSEN - FEHLER IM ALGORITHMUS?", course.getName(), course.getId());
            }
        }
    }

    /**
     * Ermittelt alle Kurse in einem Block basierend auf CourseBlockAssignment.
     */
    private List<Course> getCoursesInBlock(Block block, AssignmentState state) {
        return state.courses.stream()
                .filter(course -> {
                    Block courseBlock = state.getBlockForCourse(course);
                    return courseBlock != null && courseBlock.equals(block);
                })
                .collect(Collectors.toList());
    }

    private Course findAnyAvailableCourse(User student, Block block, AssignmentState state) {
        List<Course> coursesInBlock = getCoursesInBlock(block, state);

        return coursesInBlock.stream()
                .filter(course -> !course.isManualAssignmentOnly())
                .filter(course -> !course.isPlaceholder())
                .filter(course -> isValidAssignment(student, block, course, state, false))
                .sorted(Comparator.comparingInt(course ->
                        course.getMaxAttendees() - state.getCourseAttendees(course, block)))
                .findFirst()
                .orElse(null);
    }

    private Course findMostPopularAvailableCourse(User student, Block block, AssignmentState state) {
        List<Course> coursesInBlock = getCoursesInBlock(block, state);

        return coursesInBlock.stream()
                .filter(course -> isValidAssignmentForStudentWithoutPreference(student, block, course, state))
                .sorted(Comparator.comparingDouble(course -> {
                    int attendees = state.getCourseAttendees(course, block);
                    int maxAttendees = course.getMaxAttendees();
                    return (double) (maxAttendees - attendees) / maxAttendees;
                }))
                .findFirst()
                .orElse(null);
    }

    private boolean isValidAssignmentForStudentWithoutPreference(User student, Block block, Course course, AssignmentState state) {
        if (course.isManualAssignmentOnly()) {
            return false;
        }

        if (course.isPlaceholder()) {
            return false;
        }

        if (state.isBlockExcludedForUser(student, block)) {
            return false;
        }

        if (state.getCourseAttendees(course, block) >= course.getMaxAttendees()) {
            return false;
        }

        if (!course.getGrades().isEmpty() && !course.getGrades().contains(student.getGrade())) {
            return false;
        }

        if (course.getExcludedGenders().contains(student.getGender())) {
            return false;
        }

        Set<DayOfWeek> assignedDays = state.getAssignedDays(student);
        if (assignedDays.contains(block.getDayOfWeek())) {
            return false;
        }

        return true;
    }

    private void saveAssignments(AssignmentState state, Period period) {
        List<CourseUserAssignment> assignments = new ArrayList<>();

        for (User student : state.students) {
            for (StudentAssignment assignment : state.getAssignments(student)) {
                if (assignment.priority == -1) {
                    continue;
                }

                CourseUserAssignment cua = CourseUserAssignment.builder()
                        .user(student)
                        .course(assignment.course)
                        .block(assignment.block)
                        .preset(false)
                        .build();
                assignments.add(cua);
            }
        }

        courseUserAssignmentRepository.saveAll(assignments);
    }

    private AssignmentResult generateStatistics(AssignmentState state, int studentsWithoutPreferences) {
        int totalStudents = state.students.size();

        int assignedStudents = (int) state.students.stream()
                .filter(s -> state.getAssignmentCount(s) == COURSES_PER_STUDENT)
                .count();

        int partiallyAssigned = (int) state.students.stream()
                .filter(s -> {
                    int count = state.getAssignmentCount(s);
                    return count > 0 && count < COURSES_PER_STUDENT;
                })
                .count();

        int unassigned = (int) state.students.stream()
                .filter(s -> state.getAssignmentCount(s) == 0)
                .count();

        List<User> studentsWithPreferences = state.students.stream()
                .filter(s -> state.getAssignments(s).stream()
                        .anyMatch(a -> a.priority >= 0 && a.priority < 999))
                .collect(Collectors.toList());

        Map<Integer, Long> priorityDistribution = new HashMap<>();
        double totalPriority = 0;
        int totalAssignments = 0;

        for (User student : studentsWithPreferences) {
            for (StudentAssignment assignment : state.getAssignments(student)) {
                if (assignment.priority < 0 || assignment.priority >= 999) {
                    continue;
                }
                int displayPriority = assignment.priority + 1;
                priorityDistribution.merge(displayPriority, 1L, Long::sum);
                totalPriority += displayPriority;
                totalAssignments++;
            }
        }

        double averagePriority = totalAssignments > 0 ? totalPriority / totalAssignments : 0;

        List<Double> fairnessScores = studentsWithPreferences.stream()
                .filter(s -> state.getAssignments(s).stream()
                        .filter(a -> a.priority >= 0 && a.priority < 999)
                        .count() == COURSES_PER_STUDENT)
                .map(s -> {
                    double score = state.getAssignments(s).stream()
                            .filter(a -> a.priority >= 0 && a.priority < 999)
                            .mapToInt(a -> a.priority)
                            .average()
                            .orElse(0);
                    return score + 1.0;
                })
                .collect(Collectors.toList());

        double avgFairnessScore = fairnessScores.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);

        double stdDeviation = calculateStandardDeviation(fairnessScores, avgFairnessScore);

        return AssignmentResult.builder()
                .totalStudents(totalStudents)
                .assignedStudents(assignedStudents)
                .partiallyAssigned(partiallyAssigned)
                .unassigned(unassigned)
                .averagePriority(averagePriority)
                .priorityDistribution(priorityDistribution)
                .averageFairnessScore(avgFairnessScore)
                .fairnessStdDeviation(stdDeviation)
                .studentsWithoutPreferences(studentsWithoutPreferences)
                .build();
    }

    private double calculateStandardDeviation(List<Double> values, double mean) {
        if (values.isEmpty()) {
            return 0;
        }

        double sumSquaredDiff = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .sum();

        return Math.sqrt(sumSquaredDiff / values.size());
    }

    // Inner classes
    private static class Assignment {
        Block block;
        Course course;
        int priority;

        Assignment(Block block, Course course, int priority) {
            this.block = block;
            this.course = course;
            this.priority = priority;
        }
    }

    private static class StudentAssignment {
        Block block;
        Course course;
        int priority;

        StudentAssignment(Block block, Course course, int priority) {
            this.block = block;
            this.course = course;
            this.priority = priority;
        }
    }

    // Assignment state management
    private class AssignmentState {
        final List<User> students;
        final List<Course> courses;
        final List<Block> blocks;
        final Map<String, List<Preference>> userPreferences;
        final Map<String, List<StudentAssignment>> assignments;
        final Map<String, Integer> courseAttendees; // "courseId-blockId" -> count
        final Map<String, Set<Long>> userBlockExclusions; // userName -> excluded block IDs
        final Map<Long, Block> courseToBlock; // courseId -> Block (aus CourseBlockAssignment!)

        AssignmentState(List<User> students, List<Course> courses, List<Block> blocks,
                        Map<String, List<Preference>> userPreferences,
                        Map<String, Set<Long>> userBlockExclusions,
                        Map<Long, Block> courseToBlock) {
            this.students = students;
            this.courses = courses;
            this.blocks = blocks;
            this.userPreferences = userPreferences;
            this.userBlockExclusions = userBlockExclusions;
            this.courseToBlock = courseToBlock;
            this.assignments = new HashMap<>();
            this.courseAttendees = new HashMap<>();
        }

        void assign(User student, Block block, Course course, int priority) {
            assignments.computeIfAbsent(student.getUserName(), k -> new ArrayList<>())
                    .add(new StudentAssignment(block, course, priority));

            String key = course.getId() + "-" + block.getId();
            courseAttendees.merge(key, 1, Integer::sum);
        }

        void unassign(User student, Block block) {
            List<StudentAssignment> userAssignments = assignments.get(student.getUserName());
            if (userAssignments != null) {
                StudentAssignment toRemove = userAssignments.stream()
                        .filter(a -> a.block.equals(block))
                        .findFirst()
                        .orElse(null);

                if (toRemove != null) {
                    userAssignments.remove(toRemove);
                    String key = toRemove.course.getId() + "-" + block.getId();
                    courseAttendees.merge(key, -1, Integer::sum);
                }
            }
        }

        int getAssignmentCount(User student) {
            return assignments.getOrDefault(student.getUserName(), Collections.emptyList()).size();
        }

        List<StudentAssignment> getAssignments(User student) {
            return assignments.getOrDefault(student.getUserName(), new ArrayList<>());
        }

        Set<DayOfWeek> getAssignedDays(User student) {
            return getAssignments(student).stream()
                    .map(a -> a.block.getDayOfWeek())
                    .collect(Collectors.toSet());
        }

        Set<CourseCategory> getAssignedCategories(User student) {
            return getAssignments(student).stream()
                    .flatMap(a -> a.course.getCourseCategories().stream())
                    .collect(Collectors.toSet());
        }

        int getCourseAttendees(Course course, Block block) {
            String key = course.getId() + "-" + block.getId();
            return courseAttendees.getOrDefault(key, 0);
        }

        double getFairnessScore(User student) {
            List<StudentAssignment> userAssignments = getAssignments(student);
            if (userAssignments.isEmpty()) {
                return Double.MAX_VALUE;
            }
            return userAssignments.stream()
                    .mapToInt(a -> a.priority)
                    .average()
                    .orElse(Double.MAX_VALUE);
        }

        int getExclusionCount(User student) {
            return userBlockExclusions.getOrDefault(student.getUserName(), Collections.emptySet()).size();
        }

        int getRemainingSlots(User student) {
            return COURSES_PER_STUDENT - getAssignmentCount(student);
        }

        int getInitialAvailableBlockCount(User student) {
            Set<Long> excludedBlocks = userBlockExclusions.getOrDefault(student.getUserName(), Collections.emptySet());
            return (int) blocks.stream()
                    .filter(b -> !excludedBlocks.contains(b.getId()))
                    .count();
        }

        List<Block> getAvailableBlocks(User student) {
            Set<DayOfWeek> assignedDays = getAssignedDays(student);
            Set<Long> excludedBlocks = userBlockExclusions.getOrDefault(student.getUserName(), Collections.emptySet());
            return blocks.stream()
                    .filter(b -> !assignedDays.contains(b.getDayOfWeek()))
                    .filter(b -> !excludedBlocks.contains(b.getId()))
                    .collect(Collectors.toList());
        }

        /**
         * Ermittelt den aktuellen Block eines Kurses aus CourseBlockAssignment.
         * WICHTIG: Der Block aus Preference.getBlock() ist möglicherweise veraltet!
         */
        Block getBlockForCourse(Course course) {
            return courseToBlock.get(course.getId());
        }

        /**
         * Ermittelt Präferenzen für einen Block basierend auf dem AKTUELLEN Block des Kurses
         * (aus CourseBlockAssignment), NICHT aus Preference.block!
         */
        List<Preference> getPreferencesForBlock(User student, Block block) {
            return userPreferences.getOrDefault(student.getUserName(), Collections.emptyList())
                    .stream()
                    // WICHTIG: Den Block des Kurses aus courseToBlock ermitteln, NICHT aus p.getBlock()!
                    .filter(p -> {
                        Block courseBlock = courseToBlock.get(p.getCourse().getId());
                        return courseBlock != null && courseBlock.equals(block);
                    })
                    .sorted(Comparator.comparingInt(Preference::getPreferenceIndex))
                    .collect(Collectors.toList());
        }

        /**
         * Ermittelt die Priority für einen Kurs (unabhängig vom Block).
         * Der Block wird aus CourseBlockAssignment ermittelt.
         */
        int getPriorityForCourse(User student, Course course) {
            return userPreferences.getOrDefault(student.getUserName(), Collections.emptyList())
                    .stream()
                    .filter(p -> p.getCourse().getId() == course.getId())
                    .findFirst()
                    .map(Preference::getPreferenceIndex)
                    .orElse(Integer.MAX_VALUE);
        }

        boolean isBlockExcludedForUser(User student, Block block) {
            Set<Long> excludedBlocks = userBlockExclusions.get(student.getUserName());
            return excludedBlocks != null && excludedBlocks.contains(block.getId());
        }
    }
}