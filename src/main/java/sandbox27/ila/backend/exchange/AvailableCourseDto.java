package sandbox27.ila.backend.exchange;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AvailableCourseDto {
    private Long id;  // Course.id (eindeutige interne ID)
    private String courseId;  // Course.courseId (fachliche Kennung, nicht eindeutig über Perioden)
    private String name;
    private Long blockId;
    private String blockName;
    private String dayOfWeek;
    private int availableSpots;
    private boolean eligible;
    private String ineligibilityReason;
    /**
     * Optionale Warnung (z.B. "Kurs ist aktuell voll") - Kurs ist trotzdem wählbar
     */
    private String warning;
}