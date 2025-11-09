package sandbox27.ila.backend.assignments.algorithm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import sandbox27.ila.backend.period.Period;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "assignment_result")
public class AssignmentResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "period_id")
    private Period period;

    @Column(nullable = false)
    private LocalDateTime executedAt;

    private int totalStudents;
    private int assignedStudents;
    private int partiallyAssigned;
    private int unassigned;

    private double averagePriority;

    private boolean finalized = false;

    @ElementCollection
    @CollectionTable(
            name = "assignment_result_priority_distribution",
            joinColumns = @JoinColumn(name = "assignment_result_id")
    )
    @MapKeyColumn(name = "priority")
    @Column(name = "count")
    private Map<Integer, Long> priorityDistribution;

    private double averageFairnessScore;
    private double fairnessStdDeviation;

    @Column(name = "execution_duration_ms")
    private Long executionDurationMs;

    @Column(name = "students_without_preferences")
    private int studentsWithoutPreferences;

    @PrePersist
    protected void onCreate() {
        if (executedAt == null) {
            executedAt = LocalDateTime.now();
        }
    }

    public double getAssignmentRate() {
        return totalStudents > 0 ? (double) assignedStudents / totalStudents * 100 : 0;
    }
}