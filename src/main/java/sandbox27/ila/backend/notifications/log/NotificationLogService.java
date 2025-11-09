package sandbox27.ila.backend.notifications.log;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationLogService {

    private final NotificationLogRepository notificationLogRepository;

    public boolean hasBeenSent(String username, String topic) {
        return notificationLogRepository.existsByRecipientAndTopic(username, topic);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

    @Transactional
    public NotificationLog logNotificationSentForce(String username, String topic) {
        NotificationLog log = new NotificationLog(username, topic);
        return notificationLogRepository.save(log);
    }

    public List<NotificationLog> getLogsByTopic(String topic) {
        return notificationLogRepository.findByTopic(topic);
    }

    public List<NotificationLog> getLogsByRecipient(String username) {
        return notificationLogRepository.findByRecipient(username);
    }

    @Transactional
    public void clearLogsForTopic(String topic) {
        List<NotificationLog> logs = notificationLogRepository.findByTopic(topic);
        notificationLogRepository.deleteAll(logs);
    }
}