package sandbox27.ila.backend.user;

public enum Role {

    STUDENT("Schüler"),
    ADMIN(Role.ADMIN_ROLE_NAME),
    COURSE_INSTRUCTOR("Kursleiter"),
    SCHOOL_Admin("Schulverwaltung"),
    TREASURER("Kassierer");

    public final static String ADMIN_ROLE_NAME = "Admin";

    private final String name;

    Role(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
