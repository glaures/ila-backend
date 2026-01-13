package sandbox27.ila.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.course.*;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;

import java.io.IOException;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseImporter {

    final PeriodRepository periodRepository;
    final CourseRepository courseRepository;
    final BlockRepository blockRepository;
    final CourseBlockAssignmentRepository courseBlockAssignmentRepository;
    Period periodToImportInto;

    @Transactional
    public void runImport(Period periodToImportInto) throws IOException {
        this.periodToImportInto = periodToImportInto;
        List<ImportedCourseDto> importedCourses = importFromFile();
        importedCourses.forEach(this::storeImportedCourse);
    }

    @Transactional
    public void storeImportedCourse(ImportedCourseDto importedCourse) {
        Course course = courseRepository.findByCourseId(importedCourse.KursId).orElse(new Course());
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
        Arrays.stream(importedCourse.Klassen).forEach(ki -> course.getGrades().add(Integer.parseInt(ki)));
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
        blockRepository.save(block);
        CourseBlockAssignment courseBlockAssignment = courseBlockAssignmentRepository.findByBlockAndCourse(block, course).orElse(
                CourseBlockAssignment.builder()
                        .block(block)
                        .course(course)
                        .build()
        );
        courseBlockAssignmentRepository.save(courseBlockAssignment);
    }

    public List<ImportedCourseDto> importFromFile() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        InputStream inputStream = new ClassPathResource("kursangebote.json").getInputStream();
        return objectMapper.readValue(inputStream, new TypeReference<List<ImportedCourseDto>>() {
                }).stream()
                .filter(c -> c.Kurs != null && (!c.Kurs.contains("Hofpause") && !c.Kurs.contains("Mittagessen")))
                .collect(Collectors.toList());
    }

}