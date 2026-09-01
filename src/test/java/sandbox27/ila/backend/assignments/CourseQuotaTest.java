package sandbox27.ila.backend.assignments;

import org.junit.jupiter.api.Test;
import sandbox27.ila.backend.user.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseQuotaTest {

    @Test
    void threeCoursesUpToGradeTenTwoFromEleven() {
        assertEquals(3, CourseQuota.coursesFor(student(5)));
        assertEquals(3, CourseQuota.coursesFor(student(10)));
        assertEquals(2, CourseQuota.coursesFor(student(11)));
        assertEquals(2, CourseQuota.coursesFor(student(12)));
    }

    @Test
    void categoryMixIsRequiredOnlyBelowUpperSecondary() {
        assertTrue(CourseQuota.requiresCategoryMix(student(10)));
        assertFalse(CourseQuota.requiresCategoryMix(student(11)));
    }

    private static User student(int grade) {
        return User.builder().userName("schueler" + grade).grade(grade).build();
    }
}
