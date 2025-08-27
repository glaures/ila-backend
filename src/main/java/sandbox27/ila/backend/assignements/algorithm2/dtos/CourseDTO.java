package sandbox27.ila.backend.assignements.algorithm2.dtos;

import lombok.Data;

import java.util.Set;

@Data
public class CourseDTO {
    private String id;
    private String name;
    private int maxAttendees;
    private long blockId;
    private boolean placeholder;
}
