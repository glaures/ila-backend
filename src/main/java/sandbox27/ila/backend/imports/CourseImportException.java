package sandbox27.ila.backend.imports;

/**
 * Signals that an uploaded workbook could not be turned into rows at all (unreadable file or no
 * recognizable data sheet). The service folds the message into the report's global issues so the
 * frontend can render it like any other finding.
 */
public class CourseImportException extends RuntimeException {
    public CourseImportException(String message) {
        super(message);
    }
}
