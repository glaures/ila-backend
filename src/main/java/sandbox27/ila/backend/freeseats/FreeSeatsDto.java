package sandbox27.ila.backend.freeseats;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Eine Zelle der Kreuztabelle „freie Plätze": Klassenstufe × Block.
 * <p>
 * Der Block ist die Einheit, in der eingeschrieben wird – an einem Wochentag kann es mehrere
 * geben. Die Zeitangaben kommen im selben Format wie in
 * {@link sandbox27.ila.backend.block.BlockDto} („HH:mm").
 *
 * @param grade         Klassenstufe, 99 = Vorklasse
 * @param blockId       Block, auf den sich die Zelle bezieht
 * @param freeSeats     freie Plätze, je Kurs bei 0 abgeschnitten
 * @param capacity      Summe der maxAttendees der gezählten Kurse
 * @param assignedSeats vergebene Plätze, ungekappt – zeigt auch Überbuchungen
 * @param courseCount   Anzahl der gezählten Kurse
 */
public record FreeSeatsDto(int grade,
                           long blockId,
                           DayOfWeek dayOfWeek,
                           @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
                           LocalTime startTime,
                           @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
                           LocalTime endTime,
                           long freeSeats,
                           long capacity,
                           long assignedSeats,
                           int courseCount) {
}
