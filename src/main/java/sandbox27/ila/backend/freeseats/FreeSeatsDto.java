package sandbox27.ila.backend.freeseats;

import java.time.DayOfWeek;

/**
 * Eine Zelle der Kreuztabelle „freie Plätze": Klassenstufe × Wochentag.
 *
 * @param grade         Klassenstufe, 99 = Vorklasse
 * @param dayOfWeek     Wochentag des Blocks
 * @param freeSeats     freie Plätze, je Kurs bei 0 abgeschnitten
 * @param capacity      Summe der maxAttendees der gezählten Kurse
 * @param assignedSeats vergebene Plätze, ungekappt – zeigt auch Überbuchungen
 * @param courseCount   Anzahl der gezählten Kurse
 */
public record FreeSeatsDto(int grade,
                           DayOfWeek dayOfWeek,
                           long freeSeats,
                           long capacity,
                           long assignedSeats,
                           int courseCount) {
}
