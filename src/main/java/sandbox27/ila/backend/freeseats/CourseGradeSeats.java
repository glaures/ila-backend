package sandbox27.ila.backend.freeseats;

import java.time.DayOfWeek;

/**
 * Eine Zeile der Rohabfrage: ein Kurs, ausgeklappt auf jede Klassenstufe, die er zulässt.
 * Ein Kurs für die Stufen 5–7 erzeugt also drei Zeilen mit identischen Platzzahlen –
 * die Zusammenfassung zu Zellen passiert im {@link FreeSeatsService}.
 */
public record CourseGradeSeats(Integer grade,
                               DayOfWeek dayOfWeek,
                               int maxAttendees,
                               long assignedSeats) {
}
