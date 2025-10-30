package sandbox27.ila.backend.preference;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sandbox27.ila.backend.block.BlockDto;
import sandbox27.ila.backend.course.CourseDto;

import java.util.List;

@NoArgsConstructor
@Getter
@Setter
public class PreferencePayload {

    long blockId;
    private List<Long> preferencedCourseIds;

}
