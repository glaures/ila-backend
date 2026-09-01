package sandbox27.ila.backend.assignments;

import sandbox27.ila.backend.user.User;

/**
 * Wie viele Kurse ein Schüler in einer Phase zugewiesen bekommt.
 * <p>
 * Ab der Oberstufe sind es zwei statt drei. Die Regel steckte vorher als Konstante in jedem
 * Dienst, der sie brauchte (Vergabealgorithmus, Wechselwünsche, Problemliste) – liegt sie an
 * mehreren Stellen, laufen die Kopien beim nächsten Wechsel der Vorgabe auseinander.
 */
public final class CourseQuota {

    public final static int COURSES_PER_STUDENT = 3;
    public final static int COURSES_PER_UPPER_SECONDARY_STUDENT = 2;

    /**
     * Mindestzahl verschiedener Kategorien unter den zugewiesenen Kursen.
     */
    public final static int MIN_CATEGORIES = 2;

    /**
     * Ab dieser Klassenstufe gilt die Oberstufenregelung.
     */
    public final static int MIN_UPPER_SECONDARY_GRADE = 11;

    private CourseQuota() {
    }

    public static boolean isUpperSecondary(User student) {
        return student.getGrade() >= MIN_UPPER_SECONDARY_GRADE;
    }

    /**
     * Zahl der Kurse, die {@code student} in einer Phase bekommen soll.
     */
    public static int coursesFor(User student) {
        return isUpperSecondary(student)
                ? COURSES_PER_UPPER_SECONDARY_STUDENT
                : COURSES_PER_STUDENT;
    }

    /**
     * Ob {@link #MIN_CATEGORIES} verschiedene Kategorien gefordert sind.
     * <p>
     * Für die Oberstufe nicht: Bei nur zwei Kursen hieße die Vorgabe, dass sich beide Kurse
     * zwingend in der Kategorie unterscheiden müssen – deutlich strenger als das, was sie für
     * die Mittelstufe je bedeutet hat (zwei verschiedene unter drei Kursen). Das passt zu der
     * Entscheidung, dass die Kategorienvorgabe für die Präferenzabgabe ab Klasse 11 entfällt.
     */
    public static boolean requiresCategoryMix(User student) {
        return !isUpperSecondary(student);
    }
}
