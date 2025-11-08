package sandbox27.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import sandbox27.infrastructure.email.MailProperties;
import sandbox27.infrastructure.email.MailService;
import sandbox27.infrastructure.error.ErrorConfiguration;

import java.io.StringWriter;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApplicationStartupNotifier {

    @Value("${spring.application.name:unknown}")
    private String applicationName;
    final MailService mailService;
    final MailProperties mailProperties;
    final ErrorConfiguration errorConfiguration;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (mailService != null) {
            log.info("Mail service started.");
            log.info("Mail service configuration:");
            log.info(mailProperties.toString());
            try {
                mailService.sendSimple(mailProperties.getTo(),
                        applicationName + " Startup Notification",
                        generateStartupInformation());
            } catch (Throwable t) {
                log.error("Could not sent startup notification." , t);
            }
        }
    }

    private String generateStartupInformation() {
        return mailProperties.toString() + "\n"
                + errorConfiguration.toString();
    }

}
