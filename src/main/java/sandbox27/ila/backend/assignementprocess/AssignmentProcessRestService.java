package sandbox27.ila.backend.assignementprocess;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.assignementprocess.dtos.CourseUserAssignmentDTO;
import sandbox27.ila.backend.assignements.CourseUserAssignment;
import sandbox27.ila.backend.assignements.CourseUserAssignmentRepository;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.user.UserRepository;
import sandbox27.ila.infrastructure.error.ErrorCode;
import sandbox27.ila.infrastructure.error.ServiceException;

import java.util.List;

@RestController
@RequestMapping("/assignment-process")
@RequiredArgsConstructor
@Slf4j
public class AssignmentProcessRestService {

    final AssignmentServiceRunner assignmentServiceRunner;
    private final BlockRepository blockRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    final PeriodRepository periodRepository;
    final CourseUserAssignmentRepository courseUserAssignmentRepository;

    @Transactional
    @PostMapping("/{periodId}")
    public List<CourseUserAssignmentDTO> runAssignmentProcess(@PathVariable("periodId") Long periodId) throws ServiceException {
        Period period = periodRepository.findById(periodId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));
        List<CourseUserAssignmentDTO> assignmentDTOS = assignmentServiceRunner.runAssignmentProcess(period.getId());
        // delete current assignments that are not predefined
        int deleted = courseUserAssignmentRepository.deleteAllByPreset(false);
        log.info("Deleted {} assignments", deleted);
        for (CourseUserAssignmentDTO assignmentDto : assignmentDTOS.stream().filter(a -> !a.isPredefined() && !a.getCourseName().equals(AssignmentAlgorithmService.PAUSE_COURSE_NAME)).toList()) {
            CourseUserAssignment assignment = CourseUserAssignment.builder()
                    .block(blockRepository.getReferenceById(assignmentDto.getBlockId()))
                    .course(courseRepository.getReferenceByCourseId(assignmentDto.getCourseId()))
                    .user(userRepository.getReferenceById(assignmentDto.getUserName()))
                    .build();
            courseUserAssignmentRepository.save(assignment);
        }
        return assignmentDTOS;
    }

}
