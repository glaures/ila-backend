package sandbox27.ila.backend.exchange;

import jakarta.persistence.*;
import lombok.*;
import sandbox27.ila.backend.course.Course;

@Entity
@Table(name = "exchange_request_option")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeRequestOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchange_request_id", nullable = false)
    private ExchangeRequest exchangeRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "desired_course_id", nullable = false)
    private Course desiredCourse;

    /**
     * Priorität des Wunsches (1 = höchste Priorität)
     */
    @Column(nullable = false)
    private Integer priority;
}