package sandbox27.ila.backend.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserPrincipalSanitizingTest {

    @Test
    void keepsUsualLogins() {
        assertEquals("max.mustermann", UserManagementService.sanitizePrincipal("max.mustermann"));
        assertEquals("anne.maaß", UserManagementService.sanitizePrincipal("Anne.Maaß"));
        assertEquals("max.mustermann", UserManagementService.sanitizePrincipal("  Max.Mustermann  "));
    }

    @Test
    void replacesSpacesAndCommas() {
        assertEquals("lea-anna", UserManagementService.sanitizePrincipal("Lea, Anna"));
        assertEquals("mettke-telschow", UserManagementService.sanitizePrincipal("Mettke, Telschow"));
        assertEquals("kiethe-rudel", UserManagementService.sanitizePrincipal("Kiethe Rudel"));
    }

    @Test
    void dropsCharactersThatBreakUrlPaths() {
        assertEquals("abc", UserManagementService.sanitizePrincipal("a/b?c"));
        assertEquals("test", UserManagementService.sanitizePrincipal("..test.."));
        assertEquals("test", UserManagementService.sanitizePrincipal("--test--"));
    }

    /** Ein Principal aus lauter Punkt-Segmenten verschwindet beim Normalisieren der URL. */
    @Test
    void returnsNullWhenNothingUsableRemains() {
        assertNull(UserManagementService.sanitizePrincipal("."));
        assertNull(UserManagementService.sanitizePrincipal("   "));
        assertNull(UserManagementService.sanitizePrincipal(""));
        assertNull(UserManagementService.sanitizePrincipal(null));
    }
}
