package sandbox27.ila.tools;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;

import java.io.IOException;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Log4j2
public class ImportRunner {

    final UserImporter userImporter;
    final CourseImporter courseImporter;
    final CourseUserAssignmentImporter courseUserAssignmentImporter;
    final PeriodRepository periodRepository;
    final PlaceholderCheck placeholderCheck;

    public static final String PERIOD = "1. Quartal 25/26";

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationReady() throws IOException {
        Period periodToImportInto = periodRepository.findByName(PERIOD).orElseGet(() -> periodRepository.save(
                Period.builder()
                        .name(PERIOD)
                        .startDate(LocalDate.of(2025, 8, 21))
                        .endDate(LocalDate.of(2025, 8, 23))
                        .current(true)
                        .visible(true)
                        .build()));
        periodRepository.save(periodToImportInto);
        log.info("Starting user import");
        userImporter.runImport();
        log.info("Starting course import");
        courseImporter.runImport(periodToImportInto);
        log.info("Starting course assignment import");
        courseUserAssignmentImporter.runImport();
        log.info("All imports complete");
        // prüfen, ob jeder Block der aktuellen Phase Platzhalter hat
        placeholderCheck.ensurePlaceholdersInEveryBlockOfCurrentPeriod();
        log.info("Placeholders in every block");
    }


}
