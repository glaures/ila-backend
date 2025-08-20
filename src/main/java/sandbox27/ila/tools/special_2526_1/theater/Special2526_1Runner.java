package sandbox27.ila.tools.special_2526_1.theater;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.tools.CourseImporter;
import sandbox27.ila.tools.CourseUserAssignmentImporter;
import sandbox27.ila.tools.PlaceholderCheck;
import sandbox27.ila.tools.UserImporter;

import java.io.IOException;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Log4j2
public class Special2526_1Runner {

    final TheaterBelegung theaterBelegung;;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationReady() throws IOException {
        log.info("Starting Theaterbelegung");
        theaterBelegung.runImport();
        log.info("Specials finished");
    }


}
