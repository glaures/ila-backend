package sandbox27.ila.backend.user;

public enum GTSRole {

    student("Schüler"),
    admin("Admin"),
    courseInstructor("Kursleiter"),
    schoolAdmin("Schulverwaltung"),
    treasurer("Kassierer");

    private final String name;

    GTSRole(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
