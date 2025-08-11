package sandbox27.ila.backend.preference;

import jakarta.persistence.*;
import lombok.*;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.user.User;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "preferences")
public class Preference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private User user;

    @ManyToOne(optional = false)
    private Block block;

    @ManyToOne(optional = false)
    private Course course;

    private int preferenceIndex;

}


