package sandbox27.ila.backend.assignments;

public record StudentAssignmentSummary(
        String userName,
        long assignedCount,
        long missingCount
) {
}
