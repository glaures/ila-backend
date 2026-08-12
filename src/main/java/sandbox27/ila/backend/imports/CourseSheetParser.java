package sandbox27.ila.backend.imports;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Reads an uploaded .xlsx planning workbook and turns the relevant data sheet into a list of
 * raw string rows keyed by {@link CourseColumn}. No validation or type conversion happens here;
 * that is {@link CourseImportService}'s job.
 */
@Component
public class CourseSheetParser {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    /** How many rows from the top we scan for the header row of a sheet. */
    private static final int HEADER_SCAN_ROWS = 15;

    public record RawRow(int rowNumber, Map<CourseColumn, String> values) {
        public String get(CourseColumn c) {
            return values.getOrDefault(c, "");
        }
    }

    public record ParsedSheet(String sheetName, List<RawRow> rows) {
    }

    public ParsedSheet parse(InputStream in) {
        Workbook wb;
        try {
            // Any failure to open the stream as a workbook (wrong type, corrupt, empty) becomes a
            // friendly report message rather than a 500.
            wb = WorkbookFactory.create(in);
        } catch (IOException | RuntimeException e) {
            throw new CourseImportException(
                    "Die Datei konnte nicht als Excel-Datei (.xlsx) gelesen werden.");
        }
        try (wb) {
            DataFormatter formatter = new DataFormatter(Locale.GERMANY);
            for (Sheet sheet : wb) {
                HeaderMatch header = findHeader(sheet, formatter);
                if (header != null) {
                    return new ParsedSheet(sheet.getSheetName(), readRows(sheet, header, formatter));
                }
            }
        } catch (IOException e) {
            throw new CourseImportException(
                    "Die Datei konnte nicht als Excel-Datei (.xlsx) gelesen werden.");
        }
        throw new CourseImportException(
                "Kein Datenblatt gefunden. Es wird ein Arbeitsblatt mit den Spalten \"Kurs-ID\" und \"Titel\" erwartet.");
    }

    private record HeaderMatch(int headerRowIndex, Map<CourseColumn, Integer> columnIndex) {
    }

    /** Scans the first rows of a sheet for a row that contains at least the Kurs-ID and Titel columns. */
    private HeaderMatch findHeader(Sheet sheet, DataFormatter formatter) {
        int last = Math.min(sheet.getLastRowNum(), HEADER_SCAN_ROWS);
        for (int r = sheet.getFirstRowNum(); r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Map<CourseColumn, Integer> index = new EnumMap<>(CourseColumn.class);
            for (Cell cell : row) {
                String norm = CourseColumn.normalizeHeader(cellToString(cell, formatter));
                CourseColumn col = CourseColumn.match(norm);
                // first occurrence wins so duplicate/near-duplicate headers don't overwrite
                if (col != null) index.putIfAbsent(col, cell.getColumnIndex());
            }
            if (index.containsKey(CourseColumn.KURS_ID) && index.containsKey(CourseColumn.TITEL)) {
                return new HeaderMatch(r, index);
            }
        }
        return null;
    }

    private List<RawRow> readRows(Sheet sheet, HeaderMatch header, DataFormatter formatter) {
        List<RawRow> rows = new ArrayList<>();
        for (int r = header.headerRowIndex() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Map<CourseColumn, String> values = new EnumMap<>(CourseColumn.class);
            boolean anyValue = false;
            for (Map.Entry<CourseColumn, Integer> e : header.columnIndex().entrySet()) {
                Cell cell = row.getCell(e.getValue(), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                String value = clean(cellToString(cell, formatter));
                if (!value.isBlank()) {
                    values.put(e.getKey(), value);
                    anyValue = true;
                }
            }
            if (anyValue) {
                rows.add(new RawRow(r + 1, values)); // r+1 -> 1-based Excel row number for the user
            }
        }
        return rows;
    }

    private String cellToString(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case NUMERIC -> numericToString(cell);
            case FORMULA -> formulaToString(cell, formatter);
            default -> formatter.formatCellValue(cell);
        };
    }

    private String numericToString(Cell cell) {
        if (DateUtil.isCellDateFormatted(cell)) {
            LocalDateTime dt = cell.getLocalDateTimeCellValue();
            return dt.toLocalTime().format(TIME);
        }
        double d = cell.getNumericCellValue();
        if (d == Math.rint(d) && !Double.isInfinite(d)) {
            return Long.toString((long) d);
        }
        return Double.toString(d);
    }

    private String formulaToString(Cell cell, DataFormatter formatter) {
        try {
            return switch (cell.getCachedFormulaResultType()) {
                case STRING -> cell.getStringCellValue();
                case NUMERIC -> numericToString(cell);
                case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
                default -> formatter.formatCellValue(cell);
            };
        } catch (Exception e) {
            return "";
        }
    }

    /** Removes Excel's encoded carriage returns and trims surrounding whitespace. */
    private String clean(String s) {
        if (s == null) return "";
        return s.replace("_x000D_", "\n").replace("\r", "").trim();
    }
}
