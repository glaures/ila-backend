package sandbox27.ila.backend.freeseats;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.user.Role;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;
import sandbox27.infrastructure.security.RequiredRole;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Freie Plätze einer Phase, aufgeschlüsselt nach Klassenstufe und Block.
 * <p>
 * Beantwortet die Frage „wie viele Plätze stehen einem Kind dieser Stufe in diesem Block noch
 * offen" – Grundlage für die Übersicht über den Belegungsproblemen im Frontend. Eingeschrieben
 * wird in Blöcke, nicht in Wochentage; an einem Tag kann es mehrere geben. Plätze eines Kurses,
 * der mehrere Stufen zulässt, erscheinen bewusst in jeder dieser Zeilen; die Zellen dürfen
 * deshalb nicht über die Stufen aufsummiert werden.
 */
@RestController
@RequestMapping("/periods/{periodId}/free-seats")
@RequiredArgsConstructor
public class FreeSeatsService {

    final PeriodRepository periodRepository;
    final CourseBlockAssignmentRepository courseBlockAssignmentRepository;

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @GetMapping
    public List<FreeSeatsDto> getFreeSeats(@PathVariable Long periodId) throws ServiceException {
        if (!periodRepository.existsById(periodId))
            throw new ServiceException(ErrorCode.NotFound);

        Map<Cell, Accumulator> cells = new LinkedHashMap<>();
        for (CourseGradeSeats row : courseBlockAssignmentRepository.findCourseGradeSeatsInPeriod(periodId)) {
            if (row.grade() == null || row.blockId() == null) continue;
            cells.computeIfAbsent(new Cell(row.grade(), row.blockId()), c -> new Accumulator())
                    .add(row);
        }

        List<FreeSeatsDto> result = new ArrayList<>(cells.size());
        cells.forEach((cell, acc) -> result.add(new FreeSeatsDto(
                cell.grade(),
                cell.blockId(),
                acc.dayOfWeek,
                acc.startTime,
                acc.endTime,
                acc.freeSeats,
                acc.capacity,
                acc.assignedSeats,
                acc.courseCount)));

        result.sort(Comparator.comparingInt(FreeSeatsDto::grade)
                .thenComparing(FreeSeatsDto::dayOfWeek, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(FreeSeatsDto::startTime, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    private record Cell(int grade, long blockId) {
    }

    private static class Accumulator {
        DayOfWeek dayOfWeek;
        LocalTime startTime;
        LocalTime endTime;
        long freeSeats;
        long capacity;
        long assignedSeats;
        int courseCount;

        /**
         * Je Kurs bei 0 abschneiden: sonst würde ein überbuchter Kurs die freien Plätze der
         * anderen Kurse dieser Zelle auffressen. Die Überbuchung bleibt in assignedSeats sichtbar.
         */
        void add(CourseGradeSeats row) {
            // Alle Zeilen einer Zelle beschreiben denselben Block
            dayOfWeek = row.dayOfWeek();
            startTime = row.startTime();
            endTime = row.endTime();
            freeSeats += Math.max(0, row.maxAttendees() - row.assignedSeats());
            capacity += row.maxAttendees();
            assignedSeats += row.assignedSeats();
            courseCount++;
        }
    }
}
