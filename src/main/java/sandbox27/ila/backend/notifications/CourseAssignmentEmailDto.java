package sandbox27.ila.backend.notifications;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.Locale;

public record CourseAssignmentEmailDto(
        String courseName,
        String courseDescription,
        String instructor,
        String room,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Integer preferenceIndex  // null wenn keine Präferenz angegeben, sonst 1-basiert (1, 2, 3, ...)
) {
    // Hilfsmethode für deutschen Wochentag
    public String getDayOfWeekDisplay() {
        return dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN);
    }

    // Für Sortierung: erst nach Tag, dann nach Startzeit
    public int getSortOrder() {
        return dayOfWeek.getValue() * 10000 +
                startTime.getHour() * 100 +
                startTime.getMinute();
    }
}