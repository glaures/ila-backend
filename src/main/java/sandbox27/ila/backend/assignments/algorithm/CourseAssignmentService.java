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
        log.info("Starting course assignment for period {}", periodId);

        Period period = periodRepository.findById(periodId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound, "Period", periodId));

        // Load all necessary data
        List<User> students = userRepository.findAll().stream()
                .filter(User::isIlaMember)
                .filter(u -> u.getRoles().stream().anyMatch(r -> r.name().contains("STUDENT")))
                .collect(Collectors.toList());

        List<Course> courses = courseRepository.findAllByPeriod(period).stream()
                .filter(c -> !c.isManualAssignmentOnly())
                .filter(c -> !c.isPlaceholder())
                .collect(Collectors.toList());

        List<Block> blocks = blockRepository.findByPeriod(period);

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

        // Initialize assignment state
        AssignmentState state = new AssignmentState(students, courses, blocks, userPreferences, userBlockExclusions);

        // Load preset assignments into state
        log.info("Loading {} preset assignments into state", presetAssignments.size());
        for (CourseUserAssignment preset : presetAssignments) {
            User student = preset.getUser();
            // Priority -1 indicates a preset assignment (not from preferences)
            state.assign(student, preset.getBlock(), preset.getCourse(), -1);
        }

        // Phase 1: Greedy assignment with fairness
        log.info("Phase 1: Greedy assignment with fairness balancing");
        greedyAssignmentWithFairness(state);

        // Phase 2: Local optimization through swaps
        log.info("Phase 2: Local optimization through swaps");
        localOptimization(state);

        // Phase 3: Assign students without preferences
        log.info("Phase 3: Assigning students without preferences");
        int studentsWithoutPreferences = assignStudentsWithoutPreferences(state);

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

    private void greedyAssignmentWithFairness(AssignmentState state) {
        Random random = new Random(System.currentTimeMillis());

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            // Sort students by current fairness score (worst first)
            List<User> sortedStudents = new ArrayList<>(state.students);
            sortedStudents.sort(Comparator.comparingDouble(state::getFairnessScore).reversed());

            boolean anyAssignment = false;

            for (User student : sortedStudents) {
                if (state.getAssignmentCount(student) >= COURSES_PER_STUDENT) {
                    continue; // Student already has 3 courses
                }

                // Find best valid assignment
                Assignment bestAssignment = findBestAssignment(student, state);

                if (bestAssignment != null) {
                    state.assign(student, bestAssignment.block, bestAssignment.course, bestAssignment.priority);
                    anyAssignment = true;
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

        for (Block block : availableBlocks) {
            List<Preference> prefs = state.getPreferences(student, block);

            for (Preference pref : prefs) {
                Course course = pref.getCourse();

                if (isValidAssignment(student, block, course, state)) {
                    return new Assignment(block, course, pref.getPreferenceIndex());
                }
            }
        }

        return null;
    }

    private boolean isValidAssignment(User student, Block block, Course course, AssignmentState state) {
        // Check if user is excluded from this block
        if (state.isBlockExcludedForUser(student, block)) {
            return false;
        }

        // Check if course is full
        if (state.getCourseAttendees(course, block) >= course.getMaxAttendees()) {
            return false;
        }

        // Check grade
        if (!course.getGrades().isEmpty() && !course.getGrades().contains(student.getGrade())) {
            return false;
        }

        // Check gender exclusion
        if (course.getExcludedGenders().contains(student.getGender())) {
            return false;
        }

        // Check day conflict (no two courses on same day)
        Set<DayOfWeek> assignedDays = state.getAssignedDays(student);
        if (assignedDays.contains(block.getDayOfWeek())) {
            return false;
        }

        // Check if adding this course would allow for 2+ categories
        Set<CourseCategory> currentCategories = state.getAssignedCategories(student);
        Set<CourseCategory> newCategories = new HashSet<>(currentCategories);
        newCategories.addAll(course.getCourseCategories());

        int remainingSlots = COURSES_PER_STUDENT - state.getAssignmentCount(student) - 1;

        // If this is the last course and we don't have 2 categories yet, check if possible
        if (remainingSlots == 0 && newCategories.size() < MIN_CATEGORIES) {
            return false;
        }

        return true;
    }

    private void localOptimization(AssignmentState state) {
        Random random = new Random();

        for (int attempt = 0; attempt < SWAP_ATTEMPTS; attempt++) {
            // Pick two random students with complete assignments
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

            // Try to swap one course between them
            trySwap(student1, student2, state);
        }
    }

    private void trySwap(User student1, User student2, AssignmentState state) {
        // Create copies to avoid ConcurrentModificationException
        List<StudentAssignment> assignments1 = new ArrayList<>(state.getAssignments(student1));
        List<StudentAssignment> assignments2 = new ArrayList<>(state.getAssignments(student2));

        for (StudentAssignment a1 : assignments1) {
            for (StudentAssignment a2 : assignments2) {
                // Only swap if they're in the same block
                if (!a1.block.equals(a2.block)) {
                    continue;
                }

                // Calculate current satisfaction
                double currentSatisfaction = state.getFairnessScore(student1) + state.getFairnessScore(student2);

                // Temporarily swap
                state.unassign(student1, a1.block);
                state.unassign(student2, a2.block);

                // Check if swap is valid
                boolean swap1Valid = isValidAssignment(student1, a2.block, a2.course, state);
                boolean swap2Valid = isValidAssignment(student2, a1.block, a1.course, state);

                if (swap1Valid && swap2Valid) {
                    // Get priorities for swapped assignments
                    int priority1 = state.getPriorityForCourse(student1, a2.block, a2.course);
                    int priority2 = state.getPriorityForCourse(student2, a1.block, a1.course);

                    state.assign(student1, a2.block, a2.course, priority1);
                    state.assign(student2, a1.block, a1.course, priority2);

                    double newSatisfaction = state.getFairnessScore(student1) + state.getFairnessScore(student2);

                    // Keep swap if it improves overall satisfaction
                    if (newSatisfaction < currentSatisfaction) {
                        log.debug("Successful swap between {} and {}", student1.getUserName(), student2.getUserName());
                        return; // Keep the swap
                    } else {
                        // Revert swap
                        state.unassign(student1, a2.block);
                        state.unassign(student2, a1.block);
                        state.assign(student1, a1.block, a1.course, a1.priority);
                        state.assign(student2, a2.block, a2.course, a2.priority);
                    }
                } else {
                    // Revert invalid swap
                    state.assign(student1, a1.block, a1.course, a1.priority);
                    state.assign(student2, a2.block, a2.course, a2.priority);
                }
            }
        }
    }

    private int assignStudentsWithoutPreferences(AssignmentState state) {
        // Identify students without preferences (no assignments yet)
        List<User> studentsWithoutPreferences = state.students.stream()
                .filter(s -> state.getAssignmentCount(s) == 0)
                .collect(Collectors.toList());

        log.info("Found {} students without preferences", studentsWithoutPreferences.size());

        if (studentsWithoutPreferences.isEmpty()) {
            return 0;
        }

        // For each student without preferences, assign 3 courses
        for (User student : studentsWithoutPreferences) {
            List<Block> availableBlocks = state.getAvailableBlocks(student);

            for (Block block : availableBlocks) {
                if (state.getAssignmentCount(student) >= COURSES_PER_STUDENT) {
                    break;
                }

                // Find best course for this block based on popularity (least free spots relative to capacity)
                Course bestCourse = findMostPopularAvailableCourse(student, block, state);

                if (bestCourse != null) {
                    // Assign with a special priority marker (e.g., 999) to indicate no preference
                    state.assign(student, block, bestCourse, 999);
                    log.debug("Assigned {} to course {} (no preference)",
                            student.getUserName(), bestCourse.getName());
                }
            }

            // Log warning if student couldn't be fully assigned
            if (state.getAssignmentCount(student) < COURSES_PER_STUDENT) {
                log.warn("Student {} without preferences could only be assigned {} courses",
                        student.getUserName(), state.getAssignmentCount(student));
            }
        }

        return studentsWithoutPreferences.size();
    }

    private Course findMostPopularAvailableCourse(User student, Block block, AssignmentState state) {
        // Get all courses that are assigned to this block using repository
        List<Course> coursesInBlock = courseRepository.findAllByBlock_Id(block.getId());

        // Filter for valid assignments and sort by popularity (least free spots relative to capacity)
        return coursesInBlock.stream()
                .filter(course -> isValidAssignmentForStudentWithoutPreference(student, block, course, state))
                .sorted(Comparator.comparingDouble(course -> {
                    int attendees = state.getCourseAttendees(course, block);
                    int maxAttendees = course.getMaxAttendees();
                    // Calculate free spots ratio (lower = more popular)
                    return (double) (maxAttendees - attendees) / maxAttendees;
                }))
                .findFirst()
                .orElse(null);
    }

    private boolean isValidAssignmentForStudentWithoutPreference(User student, Block block, Course course, AssignmentState state) {
        // Use the same validation as regular assignments, but skip category check
        // since we're just trying to fill up their schedule

        // Check if user is excluded from this block
        if (state.isBlockExcludedForUser(student, block)) {
            return false;
        }

        // Check if course is full
        if (state.getCourseAttendees(course, block) >= course.getMaxAttendees()) {
            return false;
        }

        // Check grade
        if (!course.getGrades().isEmpty() && !course.getGrades().contains(student.getGrade())) {
            return false;
        }

        // Check gender exclusion
        if (course.getExcludedGenders().contains(student.getGender())) {
            return false;
        }

        // Check day conflict (no two courses on same day)
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
                // Skip preset assignments (priority -1), as they already exist in the database
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
        // ===== 1. ALLGEMEINE ZUWEISUNGSSTATISTIK (alle Schüler, alle Assignments) =====
        int totalStudents = state.students.size();

        // Schüler mit genau 3 Kursen (egal ob preset, mit Präferenz oder ohne)
        int assignedStudents = (int) state.students.stream()
                .filter(s -> state.getAssignmentCount(s) == COURSES_PER_STUDENT)
                .count();

        // Schüler mit 1-2 Kursen
        int partiallyAssigned = (int) state.students.stream()
                .filter(s -> {
                    int count = state.getAssignmentCount(s);
                    return count > 0 && count < COURSES_PER_STUDENT;
                })
                .count();

        // Schüler ohne Kurse
        int unassigned = (int) state.students.stream()
                .filter(s -> state.getAssignmentCount(s) == 0)
                .count();

        // ===== 2. PRÄFERENZ-BEZOGENE STATISTIK (nur Schüler mit Präferenzen) =====
        // Finde alle Schüler, die mindestens eine preference-basierte Assignment haben (priority 0-998)
        List<User> studentsWithPreferences = state.students.stream()
                .filter(s -> state.getAssignments(s).stream()
                        .anyMatch(a -> a.priority >= 0 && a.priority < 999))
                .collect(Collectors.toList());

        Map<Integer, Long> priorityDistribution = new HashMap<>();
        double totalPriority = 0;
        int totalAssignments = 0;

        // Zähle nur preference-basierte Assignments (priority 0-998)
        for (User student : studentsWithPreferences) {
            for (StudentAssignment assignment : state.getAssignments(student)) {
                if (assignment.priority < 0 || assignment.priority >= 999) {
                    continue; // Skip preset and "without preferences" assignments
                }
                // Convert 0-based priority to 1-based for statistics display
                int displayPriority = assignment.priority + 1;
                priorityDistribution.merge(displayPriority, 1L, Long::sum);
                totalPriority += displayPriority;
                totalAssignments++;
            }
        }

        double averagePriority = totalAssignments > 0 ? totalPriority / totalAssignments : 0;

        // Fairness: Nur für Schüler mit genau 3 preference-basierten Assignments berechnen
        // (Schüler mit preset oder "ohne Präferenz"-Kursen werden bei Fairness nicht betrachtet)
        List<Double> fairnessScores = studentsWithPreferences.stream()
                .filter(s -> state.getAssignments(s).stream()
                        .filter(a -> a.priority >= 0 && a.priority < 999)
                        .count() == COURSES_PER_STUDENT)
                .map(s -> {
                    // Calculate fairness score only from preference-based assignments
                    double score = state.getAssignments(s).stream()
                            .filter(a -> a.priority >= 0 && a.priority < 999)
                            .mapToInt(a -> a.priority)
                            .average()
                            .orElse(0);
                    return score + 1.0; // Convert to 1-based
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

        AssignmentState(List<User> students, List<Course> courses, List<Block> blocks,
                        Map<String, List<Preference>> userPreferences,
                        Map<String, Set<Long>> userBlockExclusions) {
            this.students = students;
            this.courses = courses;
            this.blocks = blocks;
            this.userPreferences = userPreferences;
            this.userBlockExclusions = userBlockExclusions;
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
                return Double.MAX_VALUE; // Worst possible score
            }
            return userAssignments.stream()
                    .mapToInt(a -> a.priority)
                    .average()
                    .orElse(Double.MAX_VALUE);
        }

        List<Block> getAvailableBlocks(User student) {
            Set<DayOfWeek> assignedDays = getAssignedDays(student);
            Set<Long> excludedBlocks = userBlockExclusions.getOrDefault(student.getUserName(), Collections.emptySet());
            return blocks.stream()
                    .filter(b -> !assignedDays.contains(b.getDayOfWeek()))
                    .filter(b -> !excludedBlocks.contains(b.getId()))
                    .collect(Collectors.toList());
        }

        List<Preference> getPreferences(User student, Block block) {
            return userPreferences.getOrDefault(student.getUserName(), Collections.emptyList())
                    .stream()
                    .filter(p -> p.getBlock().equals(block))
                    .sorted(Comparator.comparingInt(Preference::getPreferenceIndex))
                    .collect(Collectors.toList());
        }

        int getPriorityForCourse(User student, Block block, Course course) {
            return getPreferences(student, block).stream()
                    .filter(p -> p.getCourse().equals(course))
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