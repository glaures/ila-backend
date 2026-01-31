package sandbox27.ila.backend.attendance;

import sandbox27.ila.backend.absence.ExternalAbsence;

import java.time.LocalDateTime;

/**
 * DTO für Anwesenheitseinträge.
 * Enthält zusätzlich Informationen über externe Abwesenheiten aus Beste.Schule.
 */
public record AttendanceEntryDto(
        Long id,
        String userName,
        String firstName,
        String lastName,
        int grade,
        boolean present,
        String note,
        ExternalAbsenceInfo externalAbsence
) {
    /**
     * Informationen über eine externe Abwesenheit aus Beste.Schule
     */
    public record ExternalAbsenceInfo(
            boolean isAbsent,
            String absenceType,      // z.B. "krank", "Freistellung"
            LocalDateTime from,
            LocalDateTime to
    ) {
        public static ExternalAbsenceInfo fromEntity(ExternalAbsence absence) {
            if (absence == null) {
                return notAbsent();
            }
            return new ExternalAbsenceInfo(
                    true,
                    absence.getAbsenceType(),
                    absence.getFromDateTime(),
                    absence.getToDateTime()
            );
        }

        public static ExternalAbsenceInfo notAbsent() {
            return new ExternalAbsenceInfo(false, null, null, null);
        }
    }

    /**
     * Factory-Methode ohne externe Abwesenheit (Rückwärtskompatibilität)
     */
    public static AttendanceEntryDto fromEntity(AttendanceEntry entry) {
        return fromEntity(entry, null);
    }

    /**
     * Factory-Methode mit externer Abwesenheit
     */
    public static AttendanceEntryDto fromEntity(AttendanceEntry entry, ExternalAbsence externalAbsence) {
        return new AttendanceEntryDto(
                entry.getId(),
                entry.getUser().getUserName(),
                entry.getUser().getFirstName(),
                entry.getUser().getLastName(),
                entry.getUser().getGrade(),
                entry.isPresent(),
                entry.getNote(),
                ExternalAbsenceInfo.fromEntity(externalAbsence)
        );
    }
}