package sandbox27.ila.backend.preference;

import jakarta.persistence.*;
import lombok.*;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.user.User;

@Entity
@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PeriodUserPreferencesSubmitStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    long id;
    @ManyToOne
    User user;
    @ManyToOne
    Period period;
    boolean submitted = false;

}
