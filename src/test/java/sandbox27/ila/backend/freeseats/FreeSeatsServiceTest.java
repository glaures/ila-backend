package sandbox27.ila.backend.freeseats;

import org.junit.jupiter.api.Test;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Die Zusammenfassung der Kurszeilen zu Zellen der Kreuztabelle (Klassenstufe × Block).
 * Entscheidend sind zwei Regeln: ein Kurs zählt in jeder Stufe, die er zulässt, und überbuchte
 * Kurse dürfen die freien Plätze der übrigen Kurse einer Zelle nicht auffressen.
 */
class FreeSeatsServiceTest {

    private static final long PERIOD_ID = 7L;

    private final PeriodRepository periodRepository = mock(PeriodRepository.class);
    private final CourseBlockAssignmentRepository courseBlockAssignmentRepository =
            mock(CourseBlockAssignmentRepository.class);

    private final FreeSeatsService service = new FreeSeatsService(periodRepository, courseBlockAssignmentRepository);

    private void givenPeriodExists() {
        when(periodRepository.existsById(PERIOD_ID)).thenReturn(true);
    }

    private void givenRows(CourseGradeSeats... rows) {
        when(courseBlockAssignmentRepository.findCourseGradeSeatsInPeriod(PERIOD_ID)).thenReturn(List.of(rows));
    }

    /** Kurszeile in einem Block; die Blockdaten hängen an der blockId. */
    private static CourseGradeSeats row(int grade, long blockId, DayOfWeek day, String start,
                                        int maxAttendees, long assigned) {
        return new CourseGradeSeats(grade, blockId, day, LocalTime.parse(start),
                LocalTime.parse(start).plusHours(1), maxAttendees, assigned);
    }

    @Test
    void summiertKurseEinerZelle() {
        givenPeriodExists();
        givenRows(
                row(5, 1L, DayOfWeek.MONDAY, "14:00", 20, 12),
                row(5, 1L, DayOfWeek.MONDAY, "14:00", 15, 15)
        );

        List<FreeSeatsDto> result = service.getFreeSeats(PERIOD_ID);

        assertEquals(1, result.size());
        FreeSeatsDto cell = result.getFirst();
        assertEquals(8, cell.freeSeats());
        assertEquals(35, cell.capacity());
        assertEquals(27, cell.assignedSeats());
        assertEquals(2, cell.courseCount());
    }

    @Test
    void ueberbuchterKursFrisstKeineFreienPlaetze() {
        givenPeriodExists();
        givenRows(
                row(6, 2L, DayOfWeek.TUESDAY, "14:00", 10, 14),  // 4 Plätze überbucht
                row(6, 2L, DayOfWeek.TUESDAY, "14:00", 20, 5)    // 15 frei
        );

        FreeSeatsDto cell = service.getFreeSeats(PERIOD_ID).getFirst();

        assertEquals(15, cell.freeSeats(), "Überbuchung darf nicht gegengerechnet werden");
        assertEquals(19, cell.assignedSeats(), "assignedSeats bleibt ungekappt, damit die Überbuchung sichtbar ist");
    }

    @Test
    void kursZaehltInJederZugelassenenStufe() {
        givenPeriodExists();
        // Derselbe Kurs, von der Abfrage auf die Stufen 5 bis 7 aufgeklappt
        givenRows(
                row(5, 3L, DayOfWeek.WEDNESDAY, "14:00", 20, 8),
                row(6, 3L, DayOfWeek.WEDNESDAY, "14:00", 20, 8),
                row(7, 3L, DayOfWeek.WEDNESDAY, "14:00", 20, 8)
        );

        List<FreeSeatsDto> result = service.getFreeSeats(PERIOD_ID);

        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(c -> c.freeSeats() == 12 && c.courseCount() == 1));
    }

    @Test
    void trenntNachStufeUndBlockUndSortiert() {
        givenPeriodExists();
        givenRows(
                row(7, 4L, DayOfWeek.FRIDAY, "14:00", 10, 0),
                row(5, 4L, DayOfWeek.FRIDAY, "14:00", 10, 1),
                row(5, 1L, DayOfWeek.MONDAY, "14:00", 10, 2)
        );

        List<FreeSeatsDto> result = service.getFreeSeats(PERIOD_ID);

        assertEquals(
                List.of("5/MONDAY", "5/FRIDAY", "7/FRIDAY"),
                result.stream().map(c -> c.grade() + "/" + c.dayOfWeek()).toList());
    }

    /** Zwei Blöcke am selben Tag sind zwei Spalten – nach Startzeit sortiert. */
    @Test
    void trenntBloeckeDesselbenTages() {
        givenPeriodExists();
        givenRows(
                row(5, 11L, DayOfWeek.MONDAY, "15:45", 10, 4),
                row(5, 10L, DayOfWeek.MONDAY, "14:00", 10, 1)
        );

        List<FreeSeatsDto> result = service.getFreeSeats(PERIOD_ID);

        assertEquals(2, result.size());
        assertEquals(List.of(10L, 11L), result.stream().map(FreeSeatsDto::blockId).toList());
        assertEquals(9, result.getFirst().freeSeats());
        assertEquals(LocalTime.of(14, 0), result.getFirst().startTime());
    }

    @Test
    void unbekanntePhaseMeldetNotFound() {
        when(periodRepository.existsById(anyLong())).thenReturn(false);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.getFreeSeats(99L));
        assertEquals(ErrorCode.NotFound, ex.getErrorCode());
    }
}
