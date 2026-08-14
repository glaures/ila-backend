package sandbox27.ila.backend.exports;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.extensions.XSSFCellBorder.BorderSide;
import org.springframework.stereotype.Service;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.course.CourseBlockAssignment;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.backend.imports.CourseColumn;
import sandbox27.ila.backend.imports.CourseImportService;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.user.User;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Writes the courses of a period back into an .xlsx planning sheet – the mirror image of
 * {@link CourseImportService}. The typical use is to turn the current phase into the starting
 * point for the next one: export, adjust in Excel, re-import.
 * <p>
 * The sheet layout comes from {@link CourseColumn#EXPORT_COLUMNS} – the very definition the
 * parser uses to recognize columns – so both directions cannot drift apart. Values are written in
 * the spelling the import expects (weekday name + start time instead of a block, {@code 99} as
 * "VK", category short codes, instructor split into first and last name).
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CourseExportService {

    public static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** Same format the parser produces when it reads a time cell, e.g. "11:20:00". */
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String SHEET_NAME = "Kursangebot";
    /** Widths in POI units (1/256 character width) per exported column. */
    private static final int CHAR = 256;

    /** Colors of the banded green table look, mirroring the school's own planning sheets. */
    private static final byte[] HEADER_GREEN = {(byte) 0x4E, (byte) 0xA7, (byte) 0x2E};
    private static final byte[] BAND_GREEN = {(byte) 0xE2, (byte) 0xEF, (byte) 0xDA};
    private static final byte[] WHITE = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    private static final short HEADER_ROW_HEIGHT = 20;

    private final PeriodRepository periodRepository;
    private final CourseRepository courseRepository;
    private final CourseBlockAssignmentRepository courseBlockAssignmentRepository;

    /** The generated workbook plus the file name a browser should save it under. */
    public record ExportedWorkbook(String fileName, byte[] content) {
    }

    /**
     * @param periodId            phase whose courses are exported
     * @param excludePlaceholders leave out courses flagged as placeholder
     * @param onlyWithBlock       leave out courses that are not assigned to a block
     */
    public ExportedWorkbook exportCourses(Long periodId, boolean excludePlaceholders, boolean onlyWithBlock) {
        Period period = periodRepository.findById(periodId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));

        Map<Long, Block> blockByCourseId = loadBlocks(periodId);

        List<Course> courses = courseRepository.findAllByPeriod(period).stream()
                .filter(c -> !excludePlaceholders || !c.isPlaceholder())
                .filter(c -> !onlyWithBlock || blockByCourseId.containsKey(c.getId()))
                .sorted(byBlockThenCourseId(blockByCourseId))
                .toList();

        byte[] content = writeWorkbook(courses, blockByCourseId);
        log.info("Exported {} courses of period '{}'", courses.size(), period.getName());
        return new ExportedWorkbook(fileName(period), content);
    }

    // ---- workbook -------------------------------------------------------------------------------

    private byte[] writeWorkbook(List<Course> courses, Map<Long, Block> blockByCourseId) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(WorkbookUtil.createSafeSheetName(SHEET_NAME));
            Styles styles = new Styles(wb);

            Row header = sheet.createRow(0);
            header.setHeightInPoints(HEADER_ROW_HEIGHT);
            List<CourseColumn> columns = CourseColumn.EXPORT_COLUMNS;
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns.get(i).getExportHeader());
                cell.setCellStyle(styles.header());
                sheet.setColumnWidth(i, width(columns.get(i)));
            }

            int rowNum = 1;
            for (Course course : courses) {
                // banded rows, starting with a plain one right below the header
                CellStyle body = rowNum % 2 == 0 ? styles.banded() : styles.plain();
                writeRow(sheet.createRow(rowNum++), course, blockByCourseId.get(course.getId()),
                        columns, body);
            }

            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowNum - 1), 0, columns.size() - 1));
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Failed to write course export workbook", e);
            throw new ServiceException(ErrorCode.InternalServerError);
        }
    }

    private void writeRow(Row row, Course course, Block block, List<CourseColumn> columns, CellStyle style) {
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellStyle(style);
            switch (columns.get(i)) {
                case KURS_ID -> setString(cell, course.getCourseId());
                case TITEL -> setString(cell, course.getName());
                case BESCHREIBUNG -> setString(cell, course.getDescription());
                case KATEGORIE -> setString(cell, formatCategories(course));
                case KLASSEN -> setString(cell, formatGrades(course));
                case MAX_ATTENDEES -> {
                    // 0 means "not planned yet" – leave the gap visible instead of exporting a
                    // number the import would reject anyway
                    if (course.getMaxAttendees() > 0) cell.setCellValue(course.getMaxAttendees());
                }
                case RAUM -> setString(cell, course.getRoom());
                case WOCHENTAG -> setString(cell, block == null ? "" : germanDay(block.getDayOfWeek()));
                case ZEITSCHIENE -> setString(cell, block == null ? "" : formatTime(block.getStartTime()));
                case VORNAME -> setString(cell, instructorPart(course.getInstructor(), true));
                case NACHNAME -> setString(cell, instructorPart(course.getInstructor(), false));
                default -> throw new IllegalStateException(
                        "No export mapping for column " + columns.get(i));
            }
        }
    }

    /** Blank cells stay blank rather than becoming empty strings, so the sheet reads cleanly. */
    private void setString(Cell cell, String value) {
        if (value != null && !value.isBlank()) {
            cell.setCellValue(value);
        }
    }

    // ---- look of the sheet ----------------------------------------------------------------------

    /**
     * The three cell styles the sheet needs, created once per workbook (Excel only tolerates a
     * limited number of styles, so they must never be created per cell).
     */
    private static final class Styles {

        private final CellStyle header;
        private final CellStyle plain;
        private final CellStyle banded;

        Styles(XSSFWorkbook wb) {
            XSSFFont bold = wb.createFont();
            bold.setBold(true);

            header = filled(wb, HEADER_GREEN);
            header.setFont(bold);
            header.setVerticalAlignment(VerticalAlignment.CENTER);

            plain = filled(wb, null);
            banded = filled(wb, BAND_GREEN);
        }

        /** Solid fill plus the thin white grid that gives the banded table its separated look. */
        private static XSSFCellStyle filled(XSSFWorkbook wb, byte[] rgb) {
            XSSFCellStyle style = wb.createCellStyle();
            if (rgb != null) {
                style.setFillForegroundColor(new XSSFColor(rgb, null));
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            XSSFColor white = new XSSFColor(WHITE, null);
            for (BorderSide side : BorderSide.values()) {
                style.setBorderColor(side, white);
            }
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            return style;
        }

        CellStyle header() {
            return header;
        }

        CellStyle plain() {
            return plain;
        }

        CellStyle banded() {
            return banded;
        }
    }

    // ---- value formatting (mirror image of the import's parsing) --------------------------------

    private String formatCategories(Course course) {
        if (course.getCourseCategories() == null) return "";
        // short codes ("iLa", "KuP", …) – exactly what the import accepts
        return course.getCourseCategories().stream()
                .sorted(Comparator.comparing(Enum::ordinal))
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

    private String formatGrades(Course course) {
        if (course.getGrades() == null) return "";
        return course.getGrades().stream()
                .sorted()
                .map(g -> g == CourseImportService.VK_GRADE ? "VK" : String.valueOf(g))
                .collect(Collectors.joining(", "));
    }

    private String germanDay(DayOfWeek day) {
        // "Montag", "Dienstag", … – the spelling the import's weekday lookup expects
        return day == null ? "" : day.getDisplayName(TextStyle.FULL, Locale.GERMAN);
    }

    private String formatTime(LocalTime time) {
        return time == null ? "" : time.format(TIME);
    }

    private String instructorPart(User instructor, boolean firstName) {
        if (instructor == null) return "";
        String value = firstName ? instructor.getFirstName() : instructor.getLastName();
        return value == null ? "" : value;
    }

    // ---- helpers --------------------------------------------------------------------------------

    private Map<Long, Block> loadBlocks(Long periodId) {
        Map<Long, Block> map = new HashMap<>();
        for (CourseBlockAssignment cba : courseBlockAssignmentRepository.findAllByPeriodId(periodId)) {
            // a course is expected to hang on at most one block; first one wins
            map.putIfAbsent(cba.getCourse().getId(), cba.getBlock());
        }
        return map;
    }

    /** Groups the sheet the way the planning happens: by day, then time slot, then course id. */
    private Comparator<Course> byBlockThenCourseId(Map<Long, Block> blockByCourseId) {
        Comparator<Block> byDayAndTime = Comparator
                .comparing(Block::getDayOfWeek, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Block::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()));
        return Comparator
                // courses without a block go last, where the gap is easy to spot
                .comparing((Course c) -> blockByCourseId.get(c.getId()), Comparator.nullsLast(byDayAndTime))
                .thenComparing(Course::getCourseId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private int width(CourseColumn column) {
        return CHAR * switch (column) {
            case BESCHREIBUNG -> 60;
            case TITEL -> 32;
            case KATEGORIE, RAUM, WOCHENTAG, ZEITSCHIENE, VORNAME, NACHNAME -> 16;
            case MAX_ATTENDEES -> 22;
            default -> 14;
        };
    }

    /** e.g. period "1. Quartal 25/26" -> "kurs-vorlage-1-quartal-25-26.xlsx" */
    private String fileName(Period period) {
        String slug = period.getName() == null ? "" : period.getName().toLowerCase()
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return "kurs-vorlage-" + (slug.isBlank() ? period.getId() : slug) + ".xlsx";
    }
}
