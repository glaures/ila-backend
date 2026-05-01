package sandbox27.ila.backend.attendance;

import java.util.List;

/**
 * Ergebnis eines updateEntries-Aufrufs.
 *
 * @param entries  die aktualisierten Einträge (wie bisher)
 * @param warnings Hinweise an den Kursleiter, die im Frontend als Toasts
 *                 angezeigt werden sollen — z.B. wenn eine extern gemeldete
 *                 Abwesenheit nicht aus iLA heraus entfernt werden konnte oder
 *                 ein Storno in Beste.Schule technisch fehlschlug.
 */
public record UpdateAttendanceEntriesResult(
        List<AttendanceEntryDto> entries,
        List<String> warnings
) {}