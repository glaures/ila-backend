package sandbox27.ila.backend.imports;

/**
 * A single validation finding for one cell / row of an uploaded planning sheet.
 * ERROR blocks the import, WARNING does not.
 */
public record ImportIssue(Severity severity, String field, String message) {

    public enum Severity {ERROR, WARNING}

    public static ImportIssue error(String field, String message) {
        return new ImportIssue(Severity.ERROR, field, message);
    }

    public static ImportIssue warning(String field, String message) {
        return new ImportIssue(Severity.WARNING, field, message);
    }
}
