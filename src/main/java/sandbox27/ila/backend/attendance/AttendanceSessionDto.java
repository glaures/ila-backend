package sandbox27.ila.backend.attendance;

import java.time.LocalDate;

public record AttendanceSessionDto(
        Long id,
        Long courseId,
        LocalDate date,
        String notes,
        int presentCount,
        int absentCount,
        int totalCount
) {
    public static AttendanceSessionDto fromEntity(AttendanceSession session) {
        int present = 0;
        int absent = 0;
        for (AttendanceEntry entry : session.getEntries()) {
            if (entry.isPresent()) {
                present++;
            } else {
                absent++;
            }
        }
        return new AttendanceSessionDto(
                session.getId(),
                session.getCourse().getId(),
                session.getDate(),
                session.getNotes(),
                present,
                absent,
                present + absent
        );
    }
}
