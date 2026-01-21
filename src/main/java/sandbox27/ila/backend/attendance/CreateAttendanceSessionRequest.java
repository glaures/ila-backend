package sandbox27.ila.backend.attendance;

import java.time.LocalDate;

public record CreateAttendanceSessionRequest(
        Long courseId,
        LocalDate date,
        String notes
) {
}
