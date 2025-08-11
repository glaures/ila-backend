package sandbox27.ila.backend.preference;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
public class BlockPreferencesDto {

    private boolean pauseSelected;
    private List<Long> preferences;

    public BlockPreferencesDto(List<Preference> preferenceList) {
        this.preferences = preferenceList.stream()
                .map(p -> p.getCourse().getId())
                .collect(Collectors.toList());
        this.pauseSelected = !preferenceList.isEmpty() && preferenceList.get(0).getPreferenceIndex() == -1;
    }
}