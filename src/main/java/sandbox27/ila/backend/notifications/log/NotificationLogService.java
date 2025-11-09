package sandbox27.ila.backend.notifications.log;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationLogService {

    private final NotificationLogRepository notificationLogRepository;

    public NotificationLogService(NotificationLogRepository notificationLogRepository) {
        this.notificationLogRepository = notificationLogRepository;
    }

    /**
     * Prüft ob bereits eine Benachrichtigung mit diesem Topic an diesen Empfänger gesendet wurde
     */
    public boolean hasBeenSent(String username, String topic) {
        return notificationLogRepository.existsByRecipientAndTopic(username, topic);
    }

    /**
     * Loggt, dass eine Benachrichtigung mit diesem Topic an diesen Empfänger gesendet wurde
     */
    @Transactional
    public NotificationLog logNotificationSent(String username, String topic) {
        // Sicherheitscheck: Nicht doppelt loggen
        if (hasBeenSent(username, topic)) {
            throw new IllegalStateException(
                    "Benachrichtigung mit Topic '" + topic + "' wurde bereits an " + username + " gesendet"
            );
        }

        NotificationLog log = new NotificationLog(username, topic);
        return notificationLogRepository.save(log);
    }

    /**
     * Loggt eine Benachrichtigung ohne vorherigen Check (für Retry-Szenarien)
     */
    @Transactional
    public NotificationLog logNotificationSentForce(String username, String topic) {
        NotificationLog log = new NotificationLog(username, topic);
        return notificationLogRepository.save(log);
    }

    /**
     * Findet alle Empfänger, die bereits eine Benachrichtigung zu diesem Topic erhalten haben
     */
    public List<NotificationLog> getLogsByTopic(String topic) {
        return notificationLogRepository.findByTopic(topic);
    }

    /**
     * Findet alle Notification-Logs für einen bestimmten Empfänger
     */
    public List<NotificationLog> getLogsByRecipient(String username) {
        return notificationLogRepository.findByRecipient(username);
    }

    /**
     * Löscht alle Logs für einen bestimmten Topic (z.B. für Neustart)
     */
    @Transactional
    public void clearLogsForTopic(String topic) {
        List<NotificationLog> logs = notificationLogRepository.findByTopic(topic);
        notificationLogRepository.deleteAll(logs);
    }
}