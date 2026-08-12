package sandbox27.ila.backend.imports;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sandbox27.ila.backend.user.Role;
import sandbox27.infrastructure.security.RequiredRole;

/**
 * Frontend-facing endpoints for the Excel-based phase planning import.
 * <p>
 * The typical flow: the admin uploads the planning workbook to {@code /validate}, fixes whatever
 * the report flags (creating any missing instructors via {@code PUT /users} in the meantime) and
 * re-uploads until the report is clean, then calls {@code /commit} to write the courses.
 */
@RestController
@RequestMapping("/imports/courses")
@RequiredArgsConstructor
public class CourseImportController {

    private final CourseImportService courseImportService;

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CourseImportReport validate(@RequestParam("file") MultipartFile file,
                                       @RequestParam("period-id") Long periodId) {
        return courseImportService.validate(file, periodId);
    }

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @PostMapping(value = "/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CourseImportReport commit(@RequestParam("file") MultipartFile file,
                                     @RequestParam("period-id") Long periodId) {
        return courseImportService.commit(file, periodId);
    }
}
