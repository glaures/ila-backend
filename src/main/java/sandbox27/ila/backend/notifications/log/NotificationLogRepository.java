package sandbox27.ila.backend.notifications.log;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    /**
     * Prüft ob bereits eine Benachrichtigung mit diesem Topic an diesen Empfänger gesendet wurde
     */
    boolean existsByRecipientAndTopic(String recipient, String topic);

    /**
     * Findet alle Notification-Logs für einen bestimmten Topic
     */
    List<NotificationLog> findByTopic(String topic);

    /**
     * Findet alle Notification-Logs für einen bestimmten Empfänger
     */
    List<NotificationLog> findByRecipient(String recipient);
}