package sandbox27.ila.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CourseConstraintDto {

    @JsonProperty("Kurs")
    String Kurs;
    @JsonProperty("min")
    int min;
    @JsonProperty("max")
    int max;
}
