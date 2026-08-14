package sandbox27.ila.backend.exports;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sandbox27.ila.backend.exports.CourseExportService.ExportedWorkbook;
import sandbox27.ila.backend.user.Role;
import sandbox27.infrastructure.security.RequiredRole;

import java.nio.charset.StandardCharsets;

/**
 * Downloads the courses of a phase as an .xlsx sheet – the counterpart to
 * {@code POST /imports/courses/validate}. The generated file can be re-imported unchanged, which
 * makes it the starting point for planning the next phase.
 */
@RestController
@RequestMapping("/exports")
@RequiredArgsConstructor
public class CourseExportController {

    private final CourseExportService courseExportService;

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @GetMapping(value = "/courses", produces = CourseExportService.XLSX_CONTENT_TYPE)
    public ResponseEntity<byte[]> exportCourses(
            @RequestParam("period-id") Long periodId,
            @RequestParam(value = "exclude-placeholders", defaultValue = "false") boolean excludePlaceholders,
            @RequestParam(value = "only-with-block", defaultValue = "false") boolean onlyWithBlock) {

        ExportedWorkbook workbook = courseExportService.exportCourses(periodId, excludePlaceholders, onlyWithBlock);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(CourseExportService.XLSX_CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(workbook.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentLength(workbook.content().length)
                .body(workbook.content());
    }
}
