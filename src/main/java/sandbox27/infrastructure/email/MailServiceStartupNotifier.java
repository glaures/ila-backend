package sandbox27.infrastructure.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MailServiceStartupNotifier {

    @Value("${spring.application.name:unknown}")
    private String applicationName;
    final MailService mailService;
    final MailProperties mailProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if(mailService != null) {
            log.info("Mail service started.");
            log.info("Mail service configuration:");
            log.info(mailProperties.toString());
            mailService.sendSimple(mailProperties.getTo(),
                    applicationName + " Startup Notification",
                    mailProperties.toString());
        }
    }

}
