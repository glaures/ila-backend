package sandbox27.ila.backend.absence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTOs für die API-Antworten von Beste.Schule
 */
public class BesteSchuleDto {

    /**
     * Wrapper für die paginierte API-Antwort
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AbsencePageResponse(
            List<AbsenceResponse> data,
            MetaResponse meta
    ) {}

    /**
     * Meta-Informationen zur Paginierung
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MetaResponse(
            @JsonProperty("current_page") int currentPage,
            @JsonProperty("last_page") int lastPage,
            @JsonProperty("per_page") int perPage,
            int total
    ) {}

    /**
     * Eine einzelne Abwesenheitsmeldung
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AbsenceResponse(
            Long id,
            String from,  // Format: "2025-08-15 07:06:15"
            String to,    // Format: "2025-08-15 23:59:59"
            String note,
            @JsonProperty("note_teacher") String noteTeacher,
            @JsonProperty("recorded_at") String recordedAt,
            AbsenceTypeResponse type,
            StudentResponse student
    ) {}

    /**
     * Typ der Abwesenheit
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AbsenceTypeResponse(
            int id,
            String name,  // z.B. "krank", "fehlend", "Freistellung"
            @JsonProperty("default_present") int defaultPresent
    ) {}

    /**
     * Schüler-Informationen
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StudentResponse(
            Long id,
            @JsonProperty("local_id") String localId,  // UUID aus SaxSVS
            String forename,
            String name,
            String gender
    ) {}
}
