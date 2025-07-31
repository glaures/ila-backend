package sandbox27.ila.backend.block;

import jakarta.persistence.*;
import lombok.*;
import sandbox27.ila.backend.period.Period;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    long id;
    @ManyToOne
    Period period;
    DayOfWeek dayOfWeek;
    LocalTime startTime;
    LocalTime endTime;

}
