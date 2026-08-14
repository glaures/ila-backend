package sandbox27.ila.backend.imports;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The export writes {@link CourseColumn#EXPORT_COLUMNS} as its header row, so each of those headers
 * has to be recognized by the parser as the very column it was written for. Without this the
 * round-trip would break silently the next time someone touches the header matching.
 */
class CourseColumnExportHeaderTest {

    @Test
    void everyExportHeaderMatchesBackOntoItsOwnColumn() {
        for (CourseColumn column : CourseColumn.EXPORT_COLUMNS) {
            String header = column.getExportHeader();
            assertNotNull(header, column + " is exported and therefore needs a header text");
            assertEquals(column, CourseColumn.match(CourseColumn.normalizeHeader(header)),
                    "header \"" + header + "\" must be parsed back as " + column);
        }
    }

    @Test
    void exportedSheetIsRecognizedAsADataSheet() {
        // the parser only accepts a sheet that has at least these two columns
        assertTrue(CourseColumn.EXPORT_COLUMNS.contains(CourseColumn.KURS_ID));
        assertTrue(CourseColumn.EXPORT_COLUMNS.contains(CourseColumn.TITEL));
    }
}
