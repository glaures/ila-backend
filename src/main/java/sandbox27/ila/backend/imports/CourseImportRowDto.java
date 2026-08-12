package sandbox27.ila.backend.imports;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * One parsed course row of the planning sheet, with its mapped values and any validation findings.
 * Sent to the frontend so the user sees exactly what was understood and what still needs fixing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseImportRowDto {

    /** 1-based Excel row number, so the user can locate the row in their file. */
    int rowNumber;

    String courseId;
    String name;
    String description;
    @Builder.Default
    List<String> categories = new ArrayList<>();
    @Builder.Default
    List<Integer> grades = new ArrayList<>();
    Integer maxAttendees;
    String room;
    String weekday;
    String timeSlot;

    String instructorFirstName;
    String instructorLastName;
    /** Resolved once the instructor was matched against an existing user; null otherwise. */
    String instructorUserName;

    /** What the commit would do with this row. */
    Action action;

    @Builder.Default
    List<ImportIssue> issues = new ArrayList<>();

    public enum Action {CREATE, UPDATE, SKIP}

    public void addIssue(ImportIssue issue) {
        issues.add(issue);
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.severity() == ImportIssue.Severity.ERROR);
    }
}
