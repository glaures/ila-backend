package sandbox27.ila.backend.assignments;

import java.time.DayOfWeek;

/**
 * Record für Studenten mit mehreren Kursen am gleichen Wochentag.
 */
public record StudentDuplicateDayOfWeek(
        String userName,
        String firstName,
        String lastName,
        DayOfWeek dayOfWeek,
        long courseCount
) {}