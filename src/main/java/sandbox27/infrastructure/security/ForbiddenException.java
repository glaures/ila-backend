package sandbox27.infrastructure.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenException extends RuntimeException {

    public ForbiddenException() { super("Forbidden"); }
    public ForbiddenException(String message) { super(message); }
}

