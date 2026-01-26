package sandbox27.ila.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.course.*;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.user.Role;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserManagementService;
import sandbox27.ila.backend.user.UserRepository;

import java.io.IOException;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseImporter {

    final CourseRepository courseRepository;
    final BlockRepository blockRepository;
    final UserManagementService userManagementService;
    final CourseBlockAssignmentRepository courseBlockAssignmentRepository;
    private final UserRepository userRepository;
    Period periodToImportInto;

    @Transactional
    public void runImport(Period periodToImportInto) throws IOException {
        this.periodToImportInto = periodToImportInto;
        List<ImportedCourseDto> importedCourses = importFromFile();
        importedCourses.forEach(this::storeImportedCourse);
    }

    private Optional<User> findOrCreateInstructor(ImportedCourseDto importedCourseDto) {
        Optional<User> instructor = userRepository.findByFirstNameAndLastName(importedCourseDto.Vorname, importedCourseDto.Nachname);
        if (instructor.isEmpty()) {
            if (importedCourseDto.Email != null)
                instructor = Optional.of(userManagementService.createUser(importedCourseDto.Vorname, importedCourseDto.Nachname, importedCourseDto.Email, null, Role.COURSE_INSTRUCTOR.name(), true));
        }
        return instructor;
    }

    @Transactional
    public void storeImportedCourse(ImportedCourseDto importedCourse) {
        final Course course = courseRepository.findByCourseIdAndPeriod(importedCourse.KursId, periodToImportInto).orElse(new Course());
        course.setName(importedCourse.Kurs.trim());
        course.setCourseId(importedCourse.KursId.trim());
        course.setPeriod(periodToImportInto);
        course.setDescription(importedCourse.Beschreibung.replace("'", ""));
        // course.setInstructor(importedCourse.Nachname.trim());
        course.setMaxAttendees(importedCourse.maxAttendees);
        course.setRoom(importedCourse.Raum.trim());
        if (course.getCourseCategories() == null)
            course.setCourseCategories(new HashSet<>());
        Arrays.stream(importedCourse.getKategorien()).forEach(catStr -> course.getCourseCategories().add(CourseCategory.valueOf(catStr)));
        Arrays.stream(importedCourse.Klassen).forEach(ki -> course.getGrades().add(ki.equals("VK") ? 99 : Integer.parseInt(ki)));
        courseRepository.save(course);
        // String aufteilen
        String[] teile = importedCourse.Block.split(" - ");
        LocalTime start = LocalTime.parse(teile[0]);
        LocalTime ende = LocalTime.parse(teile[1]);
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN);
        TemporalAccessor accessor = formatter.parse(importedCourse.Wochentag);
        DayOfWeek dayOfWeek = DayOfWeek.from(accessor);
        Block block = blockRepository.findByPeriodAndDayOfWeekAndStartTimeAndEndTime(periodToImportInto, dayOfWeek, start, ende)
                .orElse(Block.builder()
                        .period(periodToImportInto)
                        .dayOfWeek(dayOfWeek)
                        .startTime(start)
                        .endTime(ende)
                        .build());
        block = blockRepository.save(block);
        CourseBlockAssignment courseBlockAssignment = courseBlockAssignmentRepository.findByBlockAndCourse(block, course).orElse(
                CourseBlockAssignment.builder()
                        .block(block)
                        .course(course)
                        .build()
        );
        courseBlockAssignmentRepository.save(courseBlockAssignment);
        Optional<User> instructorOpt = findOrCreateInstructor(importedCourse);
        if (instructorOpt.isEmpty())
            log.warn("No instructor for course " + importedCourse.Kurs + " could be found for name " + importedCourse.Nachname);
        else {
            course.setInstructor(instructorOpt.get());
            courseRepository.save(course);
        }
    }

    public List<ImportedCourseDto> importFromFile() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        InputStream inputStream = new ClassPathResource("kursangebote.json").getInputStream();
        return objectMapper.readValue(inputStream, new TypeReference<List<ImportedCourseDto>>() {
                }).stream()
                .filter(c -> c.Kurs != null && (!c.Kurs.contains("Hofpause") && !c.Kurs.contains("Mittagessen")
                        && c.Block != null))
                .collect(Collectors.toList());
    }

}