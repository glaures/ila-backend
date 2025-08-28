package sandbox27.ila.backend.assignementprocess.dtos;

import lombok.Data;

import java.time.LocalTime;

@Data
public class BlockDTO {
    private Long id;
    private String dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
}
