package sandbox27.ila.backend.period;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
public class Period {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    Long id;
    @Column(unique = true)
    String name;
    LocalDate startDate;
    LocalDate endDate;
    boolean visible;
    boolean current;
    @CreationTimestamp
    private LocalDateTime createdAt;
}
