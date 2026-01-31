package sandbox27.ila.backend.exchange;

import jakarta.persistence.*;
import lombok.*;
import sandbox27.ila.backend.assignments.CourseUserAssignment;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.user.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exchange_request", indexes = {
        @Index(name = "idx_exchange_request_student", columnList = "student_id"),
        @Index(name = "idx_exchange_request_period", columnList = "period_id"),
        @Index(name = "idx_exchange_request_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id", nullable = false)
    private Period period;

    /**
     * Die aktuelle Kurszuweisung, die der Schüler bereit ist abzugeben
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_assignment_id", nullable = false)
    private CourseUserAssignment currentAssignment;

    /**
     * Liste der gewünschten Kurse, geordnet nach Priorität
     */
    @OneToMany(mappedBy = "exchangeRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("priority ASC")
    @Builder.Default
    private List<ExchangeRequestOption> desiredCourses = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ExchangeRequestStatus status = ExchangeRequestStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;

    /**
     * Falls der Wunsch erfüllt wurde: mit welchem Kurs?
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fulfilled_with_course_id")
    private Course fulfilledWithCourse;

    /**
     * Begründung falls der Wunsch nicht erfüllt werden konnte
     */
    @Column(length = 500)
    private String rejectionReason;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // Hilfsmethoden

    public void addDesiredCourse(Course course, int priority) {
        ExchangeRequestOption option = ExchangeRequestOption.builder()
                .exchangeRequest(this)
                .desiredCourse(course)
                .priority(priority)
                .build();
        desiredCourses.add(option);
    }

    public void clearDesiredCourses() {
        desiredCourses.clear();
    }

    public void markAsFulfilled(Course fulfilledCourse) {
        this.status = ExchangeRequestStatus.FULFILLED;
        this.fulfilledWithCourse = fulfilledCourse;
        this.resolvedAt = LocalDateTime.now();
    }

    public void markAsUnfulfillable(String reason) {
        this.status = ExchangeRequestStatus.UNFULFILLABLE;
        this.rejectionReason = reason;
        this.resolvedAt = LocalDateTime.now();
    }

    public void withdraw() {
        this.status = ExchangeRequestStatus.WITHDRAWN;
        this.resolvedAt = LocalDateTime.now();
    }
}