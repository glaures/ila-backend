package sandbox27.ila.backend.assignements.rest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
class BlockAssignment {
    private String blockLabel;       // z.B. "Montag 09:00–10:00"
    private String courseName;       // z.B. "Töpfern"
    private Integer preferenceIndex; // z.B. 0, 1, 2, oder -1 (für PAUSE)
}