package sandbox27.infrastructure.error;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Data
@NoArgsConstructor
public class ErrorDto {

    ErrorCode code;
    String message;

    public ErrorDto(ErrorCode code, String message) {
        this.code = code;
        this.message = message;
    }

    public HttpStatusCode getHttpStatus() {
        return switch (code) {
            case ErrorCode.Unauthorized -> HttpStatus.UNAUTHORIZED;
            case ErrorCode.RoleRequired, ErrorCode.AccessDenied -> HttpStatus.FORBIDDEN;
            case ErrorCode.NotFound, ErrorCode.UserNotFound -> HttpStatus.NOT_FOUND;
            // Regelverstoß, kein Serverfehler: das Frontend soll die Meldung anzeigen können,
            // ohne den generischen Absturz-Dialog zu öffnen.
            case ErrorCode.AssignmentsAlreadyFinalized -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
