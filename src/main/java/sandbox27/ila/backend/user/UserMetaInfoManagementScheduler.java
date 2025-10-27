package sandbox27.ila.backend.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserMetaInfoManagementScheduler {

    final UserMetaInfoManagement userMetaInfoManagement;

    @Scheduled(fixedRate = 1000 * 60 * 60 * 12, initialDelay = 0) // alle 12 Stunden
    public void runSync(){
        log.info("Starting scheduled user synchronization");
        try {
            userMetaInfoManagement.syncUsers();
            log.info("Scheduled user synchronization completed successfully");
        } catch (Exception e) {
            log.error("Error during scheduled user synchronization", e);
        }
    }
}
