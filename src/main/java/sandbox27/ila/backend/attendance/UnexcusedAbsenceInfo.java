package sandbox27.ila.backend.attendance;

import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.user.User;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Informationen über eine unentschuldigte Abwesenheit.
 * Wird für die Benachrichtigung des Sekretariats verwendet.
 */
public record UnexcusedAbsenceInfo(
        // Schüler-Informationen
        String studentUserName,
        String studentFirstName,
        String studentLastName,
        int studentGrade,

        // Kurs-Informationen
        Long courseId,
        String courseName,
        String courseInstructor,

        // Termin-Informationen
        LocalDate sessionDate,
        LocalTime courseStartTime,
        LocalTime courseEndTime,
        String dayOfWeek
) {
    public static UnexcusedAbsenceInfo create(
            User student,
            Course course,
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            String dayOfWeek
    ) {
        String instructorName = course.getInstructor() != null
                ? course.getInstructor().getFirstName() + " " + course.getInstructor().getLastName()
                : "Unbekannt";

        return new UnexcusedAbsenceInfo(
                student.getUserName(),
                student.getFirstName(),
                student.getLastName(),
                student.getGrade(),
                course.getId(),
                course.getName(),
                instructorName,
                sessionDate,
                startTime,
                endTime,
                dayOfWeek
        );
    }

    public String getStudentFullName() {
        return studentFirstName() + " " + studentLastName();
    }
}