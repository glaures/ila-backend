package sandbox27.ila.backend.notifications.log;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "notification_log")
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    public NotificationLog(String recipient, String topic) {
        this.recipient = recipient;
        this.topic = topic;
        this.sentAt = LocalDateTime.now();
    }

}