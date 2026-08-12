package sandbox27.ila.backend.imports;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of validating (and optionally committing) an uploaded planning sheet. Drives the
 * frontend: it shows the per-row findings, the list of instructors still to be created, and
 * whether the import may proceed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseImportReport {

    String fileName;
    String sheetName;
    Long periodId;
    String periodName;

    int totalRows;
    int importableRows;
    int skippedRows;
    int errorCount;
    int warningCount;

    /** True only when there is not a single blocking error; a commit is allowed exactly then. */
    boolean importable;

    /** Set on the commit endpoint: whether the rows were actually written. */
    boolean committed;
    int createdCount;
    int updatedCount;

    @Builder.Default
    List<CourseImportRowDto> rows = new ArrayList<>();
    @Builder.Default
    List<MissingInstructorDto> missingInstructors = new ArrayList<>();
    @Builder.Default
    List<ImportIssue> globalIssues = new ArrayList<>();
}
