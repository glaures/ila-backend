package sandbox27.ila.backend.freeseats;

import org.junit.jupiter.api.Test;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;

import java.time.DayOfWeek;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Die Zusammenfassung der Kurszeilen zu Zellen der Kreuztabelle. Entscheidend sind zwei Regeln:
 * ein Kurs zählt in jeder Stufe, die er zulässt, und überbuchte Kurse dürfen die freien Plätze
 * der übrigen Kurse einer Zelle nicht auffressen.
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

    @Test
    void summiertKurseEinerZelle() {
        givenPeriodExists();
        givenRows(
                new CourseGradeSeats(5, DayOfWeek.MONDAY, 20, 12),
                new CourseGradeSeats(5, DayOfWeek.MONDAY, 15, 15)
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
                new CourseGradeSeats(6, DayOfWeek.TUESDAY, 10, 14),  // 4 Plätze überbucht
                new CourseGradeSeats(6, DayOfWeek.TUESDAY, 20, 5)    // 15 frei
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
                new CourseGradeSeats(5, DayOfWeek.WEDNESDAY, 20, 8),
                new CourseGradeSeats(6, DayOfWeek.WEDNESDAY, 20, 8),
                new CourseGradeSeats(7, DayOfWeek.WEDNESDAY, 20, 8)
        );

        List<FreeSeatsDto> result = service.getFreeSeats(PERIOD_ID);

        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(c -> c.freeSeats() == 12 && c.courseCount() == 1));
    }

    @Test
    void trenntNachStufeUndWochentagUndSortiert() {
        givenPeriodExists();
        givenRows(
                new CourseGradeSeats(7, DayOfWeek.FRIDAY, 10, 0),
                new CourseGradeSeats(5, DayOfWeek.FRIDAY, 10, 1),
                new CourseGradeSeats(5, DayOfWeek.MONDAY, 10, 2)
        );

        List<FreeSeatsDto> result = service.getFreeSeats(PERIOD_ID);

        assertEquals(
                List.of("5/MONDAY", "5/FRIDAY", "7/FRIDAY"),
                result.stream().map(c -> c.grade() + "/" + c.dayOfWeek()).toList());
    }

    @Test
    void unbekanntePhaseMeldetNotFound() {
        when(periodRepository.existsById(anyLong())).thenReturn(false);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.getFreeSeats(99L));
        assertEquals(ErrorCode.NotFound, ex.getErrorCode());
    }
}
