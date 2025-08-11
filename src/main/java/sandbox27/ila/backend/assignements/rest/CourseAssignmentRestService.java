package sandbox27.ila.backend.assignements.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.assignements.*;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseBlockAssignment;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.preference.Preference;
import sandbox27.ila.backend.preference.PreferenceRepository;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;
import sandbox27.ila.infrastructure.error.ServiceException;

import java.util.*;

@RestController
@RequestMapping("/assign")
@RequiredArgsConstructor
public class CourseAssignmentRestService {

    final FairCourseAssignmentService courseAssignmentService;
    final CourseRepository courseRepository;
    final CourseBlockAssignmentRepository courseBlockAssignmentRepository;
    private final PreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final BlockRepository blockRepository;
    final Comparator<Block> blockComparator = new Comparator<Block>() {

        @Override
        public int compare(Block o1, Block o2) {
            int comp1 = o1.getDayOfWeek().compareTo(o2.getDayOfWeek());
            if(comp1 != 0) return o1.getStartTime().compareTo(o2.getStartTime());
            return comp1;
        }
    };

    @PostMapping("/{periodId}")
    public AssignmentOverviewResponse runAssignmentAlgorithm(@PathVariable("periodId") Long periodId) throws ServiceException {
        BlockAssignmentRequest blockAssignmentRequest = new BlockAssignmentRequest();
        blockAssignmentRequest.setBlockCourses(generateCourseDefinitions(periodId));
        blockAssignmentRequest.setBlockPreferences(generateStudentPreferences(periodId));
        AssignmentResult assignmentResult = courseAssignmentService.assign(blockAssignmentRequest);
        return transformAssignmentResultIntoPayload(assignmentResult);
    }

    private AssignmentOverviewResponse transformAssignmentResultIntoPayload(AssignmentResult assignmentResult) {
        List<StudentAssignment> students = new ArrayList<>();
        for (String userId : assignmentResult.getAssignments().keySet()) {
            User user = userRepository.findById(userId).get();
            String studentName = user.getLastName();
            List<BlockAssignment> weeklyPlan = new ArrayList<>();

            Map<Block, Course> assignedBlocks = new HashMap<>();
            Map<Long, String> assignments = assignmentResult.getAssignments().get(userId);
            for (Long blockId : assignments.keySet()) {
                Course course;
                if(assignments.get(blockId).equals("PAUSE")) {
                    course = Course.builder().name("PAUSE").build();
                } else {
                    course = courseRepository.getReferenceById(Long.parseLong(assignments.get(blockId)));
                }
                assignedBlocks.put(blockRepository.getReferenceById(blockId), course);
            }

            for (Map.Entry<Block, Course> entry : assignedBlocks.entrySet()) {
                Block block = entry.getKey();
                Course course = entry.getValue();

                String blockLabel = formatBlockLabel(block); // z. B. "Montag 09:00–10:00"
                String courseName = course.getName();

                List<Preference> preferences = preferenceRepository.findByUserAndBlockOrderByPreferenceIndex(user, block);
                Optional<Preference> prefOpt = preferences.stream().filter(p -> p.getCourse().getId() == course.getId()).findFirst();
                Integer prefIndex = prefOpt.map(Preference::getPreferenceIndex).orElse(0);

                weeklyPlan.add(new BlockAssignment(blockLabel, courseName, prefIndex));
            }
            students.add(new StudentAssignment(studentName, weeklyPlan));
        }

        return new AssignmentOverviewResponse(students);
    }

    private List<BlockCourseDefinition> generateCourseDefinitions(Long periodId) {
        List<BlockCourseDefinition> result = new ArrayList<>();
        List<CourseBlockAssignment> courseBlockAssignments = courseBlockAssignmentRepository.findAllByPeriodId(periodId);
        for (CourseBlockAssignment courseBlockAssignment : courseBlockAssignments) {
            BlockCourseDefinition blockCourseDefinition = BlockCourseDefinition.builder()
                    .blockId(courseBlockAssignment.getBlock().getId())
                    .courseId("" + courseBlockAssignment.getCourse().getId())
                    .min(courseBlockAssignment.getCourse().getMinAttendees())
                    .max(courseBlockAssignment.getCourse().getMaxAttendees())
                    .assignedStudents(new ArrayList<>())
                    .build();
            result.add(blockCourseDefinition);
        }
        return result;
    }

    private List<BlockPreference> generateStudentPreferences(long periodId) {
        List<BlockPreference> blockPreferences = new ArrayList<>();
        List<Block> allBlocks = blockRepository.findAllByPeriod_id(periodId);
        List<User> allUsers = userRepository.findAll();
        for (User user : allUsers) {
            for (Block block : allBlocks) {
                List<Preference> userPreferences = preferenceRepository.findPreferencesByUserAndBlockOrderByPreferenceIndex(user, block);
                if (!userPreferences.isEmpty()) {
                    boolean pause = userPreferences.get(0).getPreferenceIndex() == -1;
                    BlockPreference blockPreference = BlockPreference.builder()
                            .blockId(block.getId())
                            .studentId(user.getId())
                            .preferences(pause ? Collections.emptyList() : userPreferences.stream().map(p -> "" + p.getCourse().getId()).toList())
                            .build();
                    blockPreferences.add(blockPreference);
                }
            }
        }
        return blockPreferences;
    }

    private String formatBlockLabel(Block block) {
        String weekday = switch (block.getDayOfWeek()) {
            case MONDAY -> "Montag";
            case TUESDAY -> "Dienstag";
            case WEDNESDAY -> "Mittwoch";
            case THURSDAY -> "Donnerstag";
            case FRIDAY -> "Freitag";
            default -> "";
        };
        return weekday + " " + block.getStartTime() + "–" + block.getEndTime();
    }
}