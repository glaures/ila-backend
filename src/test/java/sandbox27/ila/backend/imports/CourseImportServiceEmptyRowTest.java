package sandbox27.ila.backend.imports;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;

import java.io.ByteArrayOutputStream;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * A trailing/leftover row that still carries a stray value in one column (but has no course id and
 * no title) must be ignored, not reported as an error.
 */
class CourseImportServiceEmptyRowTest {

    private final PeriodRepository periodRepository = mock(PeriodRepository.class);
    private final BlockRepository blockRepository = mock(BlockRepository.class);
    private final CourseRepository courseRepository = mock(CourseRepository.class);
    private final CourseBlockAssignmentRepository cbaRepository = mock(CourseBlockAssignmentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private final CourseImportService service = new CourseImportService(
            new CourseSheetParser(), periodRepository, blockRepository,
            courseRepository, cbaRepository, userRepository);

    @Test
    void ignoresTrailingBlankRows() throws Exception {
        Period period = Period.builder().id(1L).name("Test-Phase").build();
        Block block = Block.builder().period(period).dayOfWeek(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(11, 20)).endTime(LocalTime.of(12, 0)).build();
        User instructor = User.builder().userName("t.abicht").firstName("Tabea").lastName("Abicht").build();

        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(blockRepository.findByPeriod(period)).thenReturn(List.of(block));
        when(userRepository.findByFirstNameAndLastName("Tabea", "Abicht")).thenReturn(Optional.of(instructor));
        when(courseRepository.findByCourseIdAndPeriod("0111", period)).thenReturn(Optional.<Course>empty());

        MockMultipartFile file = new MockMultipartFile("file", "plan.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", buildWorkbook());

        CourseImportReport report = service.validate(file, 1L);

        assertEquals(1, report.getTotalRows(), "only the real course row counts");
        // the fully empty row is dropped by the parser; the stray leftover row is skipped by the service
        assertEquals(1, report.getSkippedRows(), "the stray leftover row is skipped");
        assertEquals(0, report.getErrorCount(), "trailing blank rows must not produce errors");
        assertTrue(report.isImportable());
    }

    private byte[] buildWorkbook() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("iLa Angebote 1 Quartal");
            String[] headers = {"Nachname", "Vorname", "Kurs-ID", "Titel", "maximale Teilnehmerzahl",
                    "Klassenstufen", "Kategorie", "Raum", "Wochentag", "Zeitschiene"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            Row valid = sheet.createRow(1);
            valid.createCell(0).setCellValue("Abicht");
            valid.createCell(1).setCellValue("Tabea");
            valid.createCell(2).setCellValue("0111");
            valid.createCell(3).setCellValue("Brettspielclub");
            valid.createCell(4).setCellValue(28);
            valid.createCell(5).setCellValue("8,9,10");
            valid.createCell(6).setCellValue("Kreativität und Praxis");
            valid.createCell(7).setCellValue("224");
            valid.createCell(8).setCellValue("Montag");
            valid.createCell(9).setCellValue("11:20:00");

            // trailing row with only a leftover room value, no id / title
            Row stray = sheet.createRow(4);
            stray.createCell(7).setCellValue("224");

            // completely empty trailing row
            sheet.createRow(6);

            wb.write(out);
            return out.toByteArray();
        }
    }
}
