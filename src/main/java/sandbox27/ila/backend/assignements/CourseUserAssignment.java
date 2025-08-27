package sandbox27.ila.backend.assignements;

import jakarta.persistence.*;
import lombok.*;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.user.User;

@Entity
@Builder
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class CourseUserAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Course course;

    @ManyToOne(optional = false)
    private User user;

    @ManyToOne(optional = false)
    private Block block;

    private boolean preset = false;

}
