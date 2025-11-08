package sandbox27.ila.backend.exclusion;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.user.User;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class UserBlockExclusion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Block block;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Period period;

    private LocalDateTime createdAt;

    private String reason;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public UserBlockExclusion(User user, Block block, Period period) {
        this.user = user;
        this.block = block;
        this.period = period;
    }

    public UserBlockExclusion(User user, Block block, Period period, String reason) {
        this(user, block, period);
        this.reason = reason;
    }
}
