package sandbox27.ila.backend.imports;

import java.util.List;

/**
 * A course instructor named in the sheet that does not yet exist as a user. The frontend renders
 * these so the admin can create the users (with an email address) before re-running the import.
 */
public record MissingInstructorDto(String firstName, String lastName, List<String> courseIds) {
}
