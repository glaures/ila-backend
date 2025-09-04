package sandbox27.infrastructure.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.NOT_FOUND)
public class EntityNotFoundException extends ServiceException {
    public EntityNotFoundException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}
