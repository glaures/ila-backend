package sandbox27.ila.backend.assignable;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Ein Kurs einer Phase aus Sicht eines bestimmten Schülers: Kann er ihm manuell zugewiesen
 * werden, und wenn nicht, warum nicht?
 * <p>
 * Kurse, die für den Schüler von vornherein nicht in Frage kommen (falsche Klassenstufe,
 * ausgeschlossenes Geschlecht, gesperrter Block, persönlicher Kursausschluss, bereits zugewiesen),
 * stehen gar nicht erst in der Liste – siehe {@link AssignableCoursesService}. Die Zeitangaben
 * kommen im selben Format wie in {@link sandbox27.ila.backend.block.BlockDto} („HH:mm").
 *
 * @param dayOfWeek Name der {@link DayOfWeek}-Konstante, z.B. „MONDAY" – wie in
 *                  {@link sandbox27.ila.backend.block.BlockDto} bewusst als String. Ein Feld vom
 *                  Typ {@code DayOfWeek} liefe in den global registrierten
 *                  {@code DayOfWeekSerializer} und käme als „Montag" im Frontend an; dort werden
 *                  Wochentage aber über die Konstanten sortiert und übersetzt.
 * @param id                   Course.id (eindeutige interne ID)
 * @param courseId             Course.courseId (fachliche Kennung, Payload für POST /assignments)
 * @param blockName            sprechende Bezeichnung des Blocks, z.B. „Montag 14:00-15:30"
 * @param freeSeats            freie Plätze, bei 0 abgeschnitten
 * @param capacity             maxAttendees des Kurses
 * @param assignedSeats        vergebene Plätze, ungekappt – zeigt auch Überbuchungen
 * @param assignable           ob die Zuweisung nach den Regeln möglich ist
 * @param reason               Grund, falls nicht zuweisbar – sonst null
 * @param warning              Vorbehalt bei zuweisbaren Kursen (z.B. „Kurs ist voll") – sonst null
 * @param manualAssignmentOnly Kurs, der ausschließlich manuell vergeben wird
 */
public record AssignableCourseDto(long id,
                                  String courseId,
                                  String name,
                                  long blockId,
                                  String blockName,
                                  String dayOfWeek,
                                  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
                                  LocalTime startTime,
                                  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
                                  LocalTime endTime,
                                  int freeSeats,
                                  int capacity,
                                  int assignedSeats,
                                  boolean assignable,
                                  String reason,
                                  String warning,
                                  boolean manualAssignmentOnly) {
}
