package sandbox27.ila.backend.absence;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO für die Anzeige externer Abwesenheiten.
 */
public record ExternalAbsenceDto(
        Long id,
        Long externalId,
        String studentLocalId,
        LocalDateTime fromDateTime,
        LocalDateTime toDateTime,
        String absenceType,
        LocalDate date,
        LocalDateTime fetchedAt
) {
    public static ExternalAbsenceDto fromEntity(ExternalAbsence entity) {
        return new ExternalAbsenceDto(
                entity.getId(),
                entity.getExternalId(),
                entity.getStudentLocalId(),
                entity.getFromDateTime(),
                entity.getToDateTime(),
                entity.getAbsenceType(),
                entity.getDate(),
                entity.getFetchedAt()
        );
    }
}
