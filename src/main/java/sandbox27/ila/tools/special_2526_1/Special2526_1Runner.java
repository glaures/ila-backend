package sandbox27.ila.tools.special_2526_1;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import sandbox27.ila.tools.special_2526_1.roomandinstructor.RoomAndInstructorImporter;
import sandbox27.ila.tools.special_2526_1.six_grade_physics.SixGradePhysicsImporter;
import sandbox27.ila.tools.special_2526_1.theater.TheaterBelegung;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Log4j2
public class Special2526_1Runner {

    final TheaterBelegung theaterBelegung;;
    final SixGradePhysicsImporter sixGradePhysicsImporter;
    final RoomAndInstructorImporter roomAndInstructorImporter;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationReady() throws IOException {
        /*
        log.info("Starting Theaterbelegung");
        theaterBelegung.runImport();
        log.info("Starting Physik 6. Klasse");
        sixGradePhysicsImporter.runImport();
        log.info("Specials finished");
        roomAndInstructorImporter.runImport();
        log.info("roomAndInstructorImporter finished");
         */
    }


}
