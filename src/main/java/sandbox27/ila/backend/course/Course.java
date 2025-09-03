package sandbox27.ila.backend.course;

import jakarta.persistence.*;
import lombok.*;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.user.User;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    long id;
    String courseId;
    @ManyToOne
    Period period;
    String name;
    @Column(length = 2048)
    String description;
    @ElementCollection
    @Enumerated(EnumType.STRING)
    Set<CourseCategory> courseCategories = new HashSet<>();
    @ElementCollection
    @CollectionTable(name = "course_allowed_grades", joinColumns = @JoinColumn(name = "course_id"))
    @Column(name = "grade")
    Set<Integer> grades  = new HashSet<>();
    String room;
    int minAttendees = 0;
    int maxAttendees;
    String instructor;
    boolean placeholder = false;

}
