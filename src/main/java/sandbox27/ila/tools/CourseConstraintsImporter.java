package sandbox27.ila.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.course.*;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.user.UserRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
@RequiredArgsConstructor
@Order(6)
public class CourseConstraintsImporter {

    final PeriodRepository periodRepository;
    final CourseRepository courseRepository;
    final BlockRepository blockRepository;
    final UserRepository userRepository;
    final CourseBlockAssignmentRepository courseBlockAssignmentRepository;
    Period periodToImportInto;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationReady() throws IOException {
        runImport();
    }

    @Transactional
    public void runImport() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        InputStream inputStream = new ClassPathResource("kursteilnehmer.json").getInputStream();
        List<CourseConstraintDto> courseConstraints = mapper.readValue(inputStream, new TypeReference<>() {});
        courseConstraints.stream().forEach(cc -> {
            Optional<Course> courseOpt = courseRepository.findByName(cc.Kurs);
            if(courseOpt.isEmpty()) {
                System.out.println("keine Constraints zu " + cc.Kurs);
            } else {
                Course course = courseOpt.get();
                course.setMinAttendees(cc.min);
                course.setMaxAttendees(cc.max);
                courseRepository.save(course);
            }
        });
    }

}