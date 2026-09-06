package sandbox27.ila.backend.freeseats;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Eine Zeile der Rohabfrage: ein Kurs in seinem Block, ausgeklappt auf jede Klassenstufe, die er
 * zulässt. Ein Kurs für die Stufen 5–7 erzeugt also drei Zeilen mit identischen Platzzahlen –
 * die Zusammenfassung zu Zellen passiert im {@link FreeSeatsService}.
 */
public record CourseGradeSeats(Integer grade,
                               Long blockId,
                               DayOfWeek dayOfWeek,
                               LocalTime startTime,
                               LocalTime endTime,
                               int maxAttendees,
                               long assignedSeats) {
}
