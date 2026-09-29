package sandbox27.ila.backend.besteschule.sync;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTOs für die Beste.Schule Students API (GET /students).
 */
public class BesteSchuleStudentDto {

    /**
     * Paginierte API-Antwort für Schüler.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StudentPageResponse(
            List<StudentResponse> data,
            MetaResponse meta
    ) {}

    /**
     * Meta-Informationen zur Paginierung.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MetaResponse(
            @JsonProperty("current_page") int currentPage,
            @JsonProperty("last_page") int lastPage,
            @JsonProperty("per_page") int perPage,
            int total
    ) {}

    /**
     * Ein einzelner Schüler aus Beste.Schule.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StudentResponse(
            Long id,
            /** UUID aus SaxSVS – wird von der API seit 09/2026 nicht mehr geliefert (immer null). */
            @JsonProperty("local_id") String localId,
            String forename,
            String name,
            String gender
    ) {}
}
