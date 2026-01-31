package sandbox27.ila.backend.block;

import jakarta.persistence.*;
import lombok.*;
import sandbox27.ila.backend.period.Period;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Entity
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    long id;
    @ManyToOne
    Period period;
    DayOfWeek dayOfWeek;
    LocalTime startTime;
    LocalTime endTime;

    @Transient
    public String getName() {
        String dayName = translateDayOfWeek(this.dayOfWeek);

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        String start = this.startTime != null ? this.startTime.format(timeFormatter) : "?";
        String end = this.endTime != null ? this.endTime.format(timeFormatter) : "?";

        return dayName + " " + start + "-" + end;
    }

    private static String translateDayOfWeek(java.time.DayOfWeek day) {
        if (day == null) return "?";
        return switch (day) {
            case MONDAY -> "Montag";
            case TUESDAY -> "Dienstag";
            case WEDNESDAY -> "Mittwoch";
            case THURSDAY -> "Donnerstag";
            case FRIDAY -> "Freitag";
            case SATURDAY -> "Samstag";
            case SUNDAY -> "Sonntag";
        };
    }

}
