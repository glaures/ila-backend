package sandbox27.ila.backend.courseexclusions;

import jakarta.persistence.*;
import lombok.*;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.user.User;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "course_exclusion",
    uniqueConstraints = @UniqueConstraint(columnNames = {"course_id", "user_user_name"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseExclusion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_user_name", nullable = false)
    private User user;

    @Column(length = 512)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_name")
    private User createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
