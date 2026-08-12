package sandbox27.ila.backend.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserMetaInfoManagementTest {

    @Test
    void extractsClassSuffixFromAuxInfo() {
        assertEquals("c", UserMetaInfoManagement.extractClassSuffixFromAuxInfo("6c"));
        assertEquals("b", UserMetaInfoManagement.extractClassSuffixFromAuxInfo("10b"));
        assertEquals("_1", UserMetaInfoManagement.extractClassSuffixFromAuxInfo("10_1"));
        assertEquals("VK1", UserMetaInfoManagement.extractClassSuffixFromAuxInfo("VK1"));
        assertEquals("a", UserMetaInfoManagement.extractClassSuffixFromAuxInfo("  5a  "));
    }

    @Test
    void returnsNullWithoutSuffix() {
        assertNull(UserMetaInfoManagement.extractClassSuffixFromAuxInfo("11"));
        assertNull(UserMetaInfoManagement.extractClassSuffixFromAuxInfo(""));
        assertNull(UserMetaInfoManagement.extractClassSuffixFromAuxInfo("   "));
        assertNull(UserMetaInfoManagement.extractClassSuffixFromAuxInfo(null));
    }

    @Test
    void buildsSchoolClassFromGradeAndSuffix() {
        User user = User.builder().userName("test").grade(6).classSuffix("c").build();
        assertEquals("6c", user.getSchoolClass());

        user.setClassSuffix(null);
        assertEquals("6", user.getSchoolClass());

        user.setGrade(0);
        assertNull(user.getSchoolClass());
    }
}
