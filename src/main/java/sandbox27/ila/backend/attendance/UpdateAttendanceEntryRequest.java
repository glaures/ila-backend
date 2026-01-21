package sandbox27.ila.backend.attendance;

public record UpdateAttendanceEntryRequest(
        String userName,
        boolean present,
        String note
) {
}
