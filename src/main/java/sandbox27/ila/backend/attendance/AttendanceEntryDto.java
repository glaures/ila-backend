package sandbox27.ila.backend.attendance;

public record AttendanceEntryDto(
        Long id,
        String userName,
        String firstName,
        String lastName,
        int grade,
        boolean present,
        String note
) {
    public static AttendanceEntryDto fromEntity(AttendanceEntry entry) {
        return new AttendanceEntryDto(
                entry.getId(),
                entry.getUser().getUserName(),
                entry.getUser().getFirstName(),
                entry.getUser().getLastName(),
                entry.getUser().getGrade(),
                entry.isPresent(),
                entry.getNote()
        );
    }
}
