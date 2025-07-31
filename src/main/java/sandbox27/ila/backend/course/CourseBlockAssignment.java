package sandbox27.ila.backend.course;

import jakarta.persistence.*;
import lombok.*;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.period.Period;

@Entity
@Builder
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class CourseBlockAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Course course;

    @ManyToOne(optional = false)
    private Block block;

    @ManyToOne(optional = false)
    private Period period;

}
