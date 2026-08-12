package sandbox27.ila.backend.imports;

import org.junit.jupiter.api.Test;
import sandbox27.ila.backend.imports.CourseSheetParser.ParsedSheet;
import sandbox27.ila.backend.imports.CourseSheetParser.RawRow;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the parser against the real planning workbooks in {@code docs/} so we know the column
 * mapping copes with both header variants (with and without the "Angebot 1:" prefix) and the clean
 * import template. Pure unit test, no Spring context / DB needed.
 */
class CourseSheetParserTest {

    private final CourseSheetParser parser = new CourseSheetParser();

    private ParsedSheet parseDoc(String fileName) throws Exception {
        try (InputStream in = Files.newInputStream(Path.of("docs", fileName))) {
            return parser.parse(in);
        }
    }

    @Test
    void parsesEngelsPlanningSheetVariantA() throws Exception {
        ParsedSheet sheet = parseDoc("iLa_SJ2526_Kurse-A1.xlsx");

        assertTrue(sheet.sheetName().toLowerCase().contains("angebote"),
                "should pick the offers sheet, not a pivot sheet, but was: " + sheet.sheetName());
        assertTrue(sheet.rows().size() > 100, "expected all course rows, was " + sheet.rows().size());

        RawRow first = sheet.rows().get(0);
        assertEquals("0111", first.get(CourseColumn.KURS_ID));
        assertEquals("Brettspielclub", first.get(CourseColumn.TITEL));
        assertEquals("Montag", first.get(CourseColumn.WOCHENTAG));
        assertEquals("11:20:00", first.get(CourseColumn.ZEITSCHIENE));
        assertEquals("Kreativität und Praxis", first.get(CourseColumn.KATEGORIE));
        assertEquals("28", first.get(CourseColumn.MAX_ATTENDEES));
        assertEquals("Abicht", first.get(CourseColumn.NACHNAME));
        assertEquals("Tabea", first.get(CourseColumn.VORNAME));
        // "Angebot 1: Klassenstufen" must map, "Klasse/Information" must NOT be treated as grades
        assertEquals("8,9,10", first.get(CourseColumn.KLASSEN));
    }

    @Test
    void parsesPlanningSheetVariantBWithoutOfferPrefix() throws Exception {
        ParsedSheet sheet = parseDoc("iLa_SJ2526_Kurse-B1.xlsx");

        assertTrue(sheet.rows().size() > 100);
        RawRow first = sheet.rows().get(0);
        assertEquals("1001", first.get(CourseColumn.KURS_ID));
        assertEquals("Brettspielclub", first.get(CourseColumn.TITEL));
        assertEquals("224", first.get(CourseColumn.RAUM));
        assertEquals("Montag", first.get(CourseColumn.WOCHENTAG));
    }

    @Test
    void parsesCleanImportTemplateWithCombinedInstructorAndEndTime() throws Exception {
        ParsedSheet sheet = parseDoc("Kurs_Import_Format.xlsx");

        assertEquals(1, sheet.rows().size());
        RawRow row = sheet.rows().get(0);
        assertEquals("48114", row.get(CourseColumn.KURS_ID));
        assertEquals("10 Finger Tippen", row.get(CourseColumn.TITEL));
        assertEquals("iLa, KuP", row.get(CourseColumn.KATEGORIE));
        assertEquals("11:00:00", row.get(CourseColumn.ZEITSCHIENE)); // "Start"
        assertEquals("12:00:00", row.get(CourseColumn.ENDE));
        assertEquals("Cindy Darius", row.get(CourseColumn.KURSLEITER));
    }
}
