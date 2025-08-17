package sandbox27.ila.backend.user;

public enum Role {

    STUDENT("Schüler"),
    ADMIN("Admin"),
    COURSE_INSTRUCTOR("Kursleiter"),
    SCHOOL_Admin("Schulverwaltung"),
    TREASURER("Kassierer");

    private final String name;

    Role(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
