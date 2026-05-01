package sandbox27.ila.backend.attendance;

import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.user.User;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Informationen über eine unentschuldigte Abwesenheit.
 * Wird für die Meldung an Beste.Schule verwendet.
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
        String dayOfWeek,

        // Wer meldet die Abwesenheit (kann vom Kursleiter abweichen,
        // z.B. wenn ein Admin oder Vertretungslehrer Anwesenheiten erfasst)
        String reportingTeacherFullName
) {
    public static UnexcusedAbsenceInfo create(
            User student,
            Course course,
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            String dayOfWeek,
            User reportingTeacher
    ) {
        String instructorName = course.getInstructor() != null
                ? course.getInstructor().getFirstName() + " " + course.getInstructor().getLastName()
                : "Unbekannt";

        String reporterName = reportingTeacher != null
                ? (reportingTeacher.getFirstName() + " " + reportingTeacher.getLastName()).trim()
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
                dayOfWeek,
                reporterName
        );
    }

    public String getStudentFullName() {
        return studentFirstName() + " " + studentLastName();
    }
}