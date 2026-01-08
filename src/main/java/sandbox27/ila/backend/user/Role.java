package sandbox27.ila.backend.user;

import java.util.Arrays;

public enum Role {

    STUDENT(Role.STUDENT_ROLE_NAME),
    ADMIN(Role.ADMIN_ROLE_NAME),
    COURSE_INSTRUCTOR(Role.COURSE_INSTRUCTOR_ROLE_NAME);

    public final static String ADMIN_ROLE_NAME = "Admin";
    public final static String STUDENT_ROLE_NAME = "Schüler";
    public final static String COURSE_INSTRUCTOR_ROLE_NAME = "Kursleiter";

    private final String name;

    Role(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static boolean isValidRole(String value) {
        return Arrays.stream(Role.values())
                .anyMatch(e -> e.name().equals(value));
    }
}
