package sandbox27.ila.backend.imports.besteschule;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sandbox27.infrastructure.security.RequiredRole;
import sandbox27.ila.backend.user.Role;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/import/besteschule")
public class BesteSchuleImportController {
    
    private static final Logger log = LoggerFactory.getLogger(BesteSchuleImportController.class);
    
    private final BesteSchuleImportService importService;
    private final ObjectMapper objectMapper;
    
    public BesteSchuleImportController(BesteSchuleImportService importService, ObjectMapper objectMapper) {
        this.importService = importService;
        this.objectMapper = objectMapper;
    }
    
    @PostMapping
    @RequiredRole(Role.ADMIN_ROLE_NAME)
    public ResponseEntity<?> importUsers(@RequestParam("file") MultipartFile file) {
        log.info("Received import request with file: {}", file.getOriginalFilename());
        
        // Validate file
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("Die hochgeladene Datei ist leer"));
        }
        
        if (!file.getOriginalFilename().toLowerCase().endsWith(".json")) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("Nur JSON-Dateien werden akzeptiert"));
        }
        
        try {
            // Parse JSON file
            BesteSchuleUserDto[] usersArray = objectMapper.readValue(
                    file.getInputStream(), 
                    BesteSchuleUserDto[].class
            );
            
            List<BesteSchuleUserDto> users = Arrays.asList(usersArray);
            log.info("Parsed {} users from JSON file", users.size());
            
            // Process import
            ImportResult result = importService.importUsers(users);
            
            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("success", !result.hasErrors());
            response.put("message", result.getSummary());
            response.put("total", result.getTotal());
            response.put("updated", result.getUpdated());
            response.put("notFound", result.getNotFound());
            response.put("errors", result.getErrors());
            
            if (!result.getErrorMessages().isEmpty()) {
                response.put("errorDetails", result.getErrorMessages());
            }
            
            HttpStatus status = result.hasErrors() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK;
            return ResponseEntity.status(status).body(response);
            
        } catch (IOException e) {
            log.error("Error parsing JSON file", e);
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("Fehler beim Lesen der JSON-Datei: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during import", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Interner Fehler beim Import: " + e.getMessage()));
        }
    }
    
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return response;
    }
}
