package sandbox27.ila.backend.exclusion;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchExclusionResult {
    private int added;
    private int skipped;
    private int total;
    private String message;

    public static BatchExclusionResult success(int added, int skipped) {
        return new BatchExclusionResult(
                added,
                skipped,
                added + skipped,
                String.format("%d Ausnahme(n) hinzugefügt, %d übersprungen (bereits vorhanden)",
                        added, skipped)
        );
    }
}