package sandbox27.ila.backend.assignements;

public record StudentAssignmentSummary(
        String userName,
        long assignedCount,
        long missingCount
) {
}
