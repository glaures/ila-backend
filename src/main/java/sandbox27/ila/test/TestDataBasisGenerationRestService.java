package sandbox27.ila.test;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;

@RestController
@RequestMapping("/test-basis")
@RequiredArgsConstructor
public class TestDataBasisGenerationRestService {

    final PeriodRepository periodRepository;
    final CourseRepository courseRepository;
    final UserRepository userRepository;
    final BlockRepository blockRepository;
    final CourseBlockAssignmentRepository courseBlockAssignmentRepository;

    @GetMapping
    public TestBasisData generateTestBasisData(){
        TestBasisData testBasisData = new TestBasisData();
        testBasisData.blocks = blockRepository.findAll();
        testBasisData.courses = courseRepository.findAll();
        testBasisData.users = userRepository.findAll();
        testBasisData.courseBlockAssignments = courseBlockAssignmentRepository.findAll();
        return testBasisData;
    }
}
