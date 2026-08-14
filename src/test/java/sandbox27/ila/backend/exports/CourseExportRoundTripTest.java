package sandbox27.ila.backend.exports;

import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.course.*;
import sandbox27.ila.backend.exports.CourseExportService.ExportedWorkbook;
import sandbox27.ila.backend.imports.*;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;

import java.io.ByteArrayInputStream;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Proves that export and import speak the same layout: exporting a phase and feeding the result
 * straight back into {@code validate} must yield a clean report in which every row is an UPDATE of
 * the course it came from. Pure unit test with mocked repositories, no Spring context / DB needed.
 */
class CourseExportRoundTripTest {

    private final PeriodRepository periodRepository = mock(PeriodRepository.class);
    private final BlockRepository blockRepository = mock(BlockRepository.class);
    private final CourseRepository courseRepository = mock(CourseRepository.class);
    private final CourseBlockAssignmentRepository cbaRepository = mock(CourseBlockAssignmentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private final CourseExportService exportService =
            new CourseExportService(periodRepository, courseRepository, cbaRepository);
    private final CourseImportService importService = new CourseImportService(
            new CourseSheetParser(), periodRepository, blockRepository,
            courseRepository, cbaRepository, userRepository);

    private final Period period = Period.builder().id(1L).name("1. Quartal 25/26").build();
    private final Block monday = Block.builder().id(10L).period(period).dayOfWeek(DayOfWeek.MONDAY)
            .startTime(LocalTime.of(11, 20)).endTime(LocalTime.of(12, 0)).build();
    private final Block thursday = Block.builder().id(11L).period(period).dayOfWeek(DayOfWeek.THURSDAY)
            .startTime(LocalTime.of(13, 45)).endTime(LocalTime.of(14, 30)).build();
    private final User instructor = User.builder().userName("t.abicht")
            .firstName("Tabea").lastName("Abicht").build();

    @Test
    void exportedSheetReImportsAsPureUpdates() {
        Course boardGames = course(100L, "0111", "Brettspielclub", "Spiele für alle",
                Set.of(CourseCategory.KuP), Set.of(8, 9, 10), 28, "224");
        Course german = course(101L, "0207", "Deutsch für Anfänger", "Sprachförderung",
                Set.of(CourseCategory.FuF, CourseCategory.SOL), Set.of(5, 6, CourseImportService.VK_GRADE), 12, "A1.03");

        givenPeriodWithCourses(List.of(boardGames, german),
                List.of(assignment(boardGames, thursday), assignment(german, monday)));

        CourseImportReport report = reImport(exportService.exportCourses(1L, false, false));

        assertEquals(0, report.getErrorCount(), () -> "unexpected errors: " + allIssues(report));
        assertTrue(report.isImportable(), "an exported sheet must be importable as-is");
        assertEquals(2, report.getTotalRows());
        assertTrue(report.getRows().stream()
                        .allMatch(r -> r.getAction() == CourseImportRowDto.Action.UPDATE),
                "every exported course must be recognized again, not created anew");

        // values survive the round-trip in the spelling the import expects
        CourseImportRowDto first = report.getRows().get(0);
        assertEquals("0207", first.getCourseId(), "rows are ordered by block: Monday before Thursday");
        assertEquals("Montag", first.getWeekday());
        assertEquals("11:20:00", first.getTimeSlot());
        assertEquals(List.of(5, 6, CourseImportService.VK_GRADE), first.getGrades(), "VK round-trips as 99");
        assertEquals(List.of("FuF", "SOL"), first.getCategories());
        assertEquals("Tabea", first.getInstructorFirstName());
        assertEquals("Abicht", first.getInstructorLastName());
        assertEquals("t.abicht", first.getInstructorUserName());
        assertEquals("Sprachförderung", first.getDescription());
        assertEquals("A1.03", first.getRoom());
        assertEquals(12, first.getMaxAttendees());
    }

    @Test
    void coursesWithoutBlockOrInstructorKeepTheGapVisible() {
        Course incomplete = course(102L, "0999", "Noch offen", "", Set.of(CourseCategory.iLa),
                Set.of(7), 15, "");
        incomplete.setInstructor(null);

        givenPeriodWithCourses(List.of(incomplete), List.of());

        CourseImportReport report = reImport(exportService.exportCourses(1L, false, false));

        assertFalse(report.isImportable(), "the missing block and instructor must show up as errors");
        assertEquals(1, report.getTotalRows());
        Set<String> fields = report.getRows().get(0).getIssues().stream()
                .map(ImportIssue::field).collect(java.util.stream.Collectors.toSet());
        assertTrue(fields.contains("weekday"), "expected a weekday issue, got " + fields);
        assertTrue(fields.contains("timeSlot"), "expected a timeSlot issue, got " + fields);
        assertTrue(fields.contains("instructor"), "expected an instructor issue, got " + fields);
    }

    @Test
    void filtersPlaceholdersAndCoursesWithoutBlock() {
        Course real = course(100L, "0111", "Brettspielclub", "", Set.of(CourseCategory.KuP), Set.of(8), 28, "224");
        Course placeholder = course(103L, "0900", "Platzhalter", "", Set.of(CourseCategory.iLa), Set.of(8), 5, "");
        placeholder.setPlaceholder(true);
        Course unplanned = course(104L, "0901", "Ohne Block", "", Set.of(CourseCategory.iLa), Set.of(8), 5, "");

        givenPeriodWithCourses(List.of(real, placeholder, unplanned),
                List.of(assignment(real, monday), assignment(placeholder, monday)));

        assertEquals(3, reImport(exportService.exportCourses(1L, false, false)).getTotalRows());
        assertEquals(2, reImport(exportService.exportCourses(1L, true, false)).getTotalRows(),
                "exclude-placeholders drops the placeholder course");
        assertEquals(2, reImport(exportService.exportCourses(1L, false, true)).getTotalRows(),
                "only-with-block drops the unplanned course");
        assertEquals(1, reImport(exportService.exportCourses(1L, true, true)).getTotalRows(),
                "both filters combine");
    }

    @Test
    void sheetUsesTheBandedGreenLayout() throws Exception {
        Course a = course(100L, "0111", "Brettspielclub", "", Set.of(CourseCategory.KuP), Set.of(8), 28, "224");
        Course b = course(101L, "0207", "Deutsch", "", Set.of(CourseCategory.FuF), Set.of(5), 12, "A1.03");
        givenPeriodWithCourses(List.of(a, b), List.of(assignment(a, monday), assignment(b, thursday)));

        byte[] content = exportService.exportCourses(1L, false, false).content();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            XSSFSheet sheet = wb.getSheetAt(0);

            assertEquals("FF4EA72E", fillOf(sheet.getRow(0).getCell(0)), "green header band");
            assertTrue(sheet.getRow(0).getCell(0).getCellStyle().getFont().getBold(), "bold header");
            assertNull(fillOf(sheet.getRow(1).getCell(0)), "first data row stays plain");
            assertEquals("FFE2EFDA", fillOf(sheet.getRow(2).getCell(0)), "second data row is banded");

            assertEquals("A1:K3", sheet.getCTWorksheet().getAutoFilter().getRef(),
                    "filter dropdowns must span the whole table");
            assertEquals(1, sheet.getPaneInformation().getHorizontalSplitPosition(),
                    "header row stays visible while scrolling");
        }
    }

    /** ARGB hex of a cell's background, or null if it has none. */
    private String fillOf(org.apache.poi.ss.usermodel.Cell cell) {
        XSSFColor color = ((org.apache.poi.xssf.usermodel.XSSFCellStyle) cell.getCellStyle())
                .getFillForegroundColorColor();
        return color == null ? null : color.getARGBHex();
    }

    @Test
    void fileNameIsDerivedFromThePeriodName() {
        givenPeriodWithCourses(List.of(), List.of());
        assertEquals("kurs-vorlage-1-quartal-25-26.xlsx", exportService.exportCourses(1L, false, false).fileName());
    }

    // ---- helpers --------------------------------------------------------------------------------

    private void givenPeriodWithCourses(List<Course> courses, List<CourseBlockAssignment> assignments) {
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(courseRepository.findAllByPeriod(period)).thenReturn(courses);
        when(cbaRepository.findAllByPeriodId(1L)).thenReturn(assignments);
        when(blockRepository.findByPeriod(period)).thenReturn(List.of(monday, thursday));
        when(userRepository.findByFirstNameAndLastName("Tabea", "Abicht")).thenReturn(Optional.of(instructor));
        for (Course c : courses) {
            when(courseRepository.findByCourseIdAndPeriod(c.getCourseId(), period)).thenReturn(Optional.of(c));
        }
    }

    /** Runs the exported bytes back through the import's validation, as the frontend would. */
    private CourseImportReport reImport(ExportedWorkbook workbook) {
        return importService.validate(new MockMultipartFile("file", workbook.fileName(),
                CourseExportService.XLSX_CONTENT_TYPE, workbook.content()), 1L);
    }

    private Course course(long id, String courseId, String name, String description,
                          Set<CourseCategory> categories, Set<Integer> grades, int maxAttendees, String room) {
        return Course.builder()
                .id(id).courseId(courseId).period(period).name(name).description(description)
                .courseCategories(new java.util.HashSet<>(categories))
                .grades(new java.util.HashSet<>(grades))
                .excludedGenders(new java.util.HashSet<>())
                .room(room).maxAttendees(maxAttendees).instructor(instructor)
                .build();
    }

    private CourseBlockAssignment assignment(Course course, Block block) {
        return CourseBlockAssignment.builder().course(course).block(block).build();
    }

    private String allIssues(CourseImportReport report) {
        return report.getRows().stream()
                .flatMap(r -> r.getIssues().stream().map(i -> r.getCourseId() + ": " + i.message()))
                .toList() + " " + report.getGlobalIssues();
    }
}
