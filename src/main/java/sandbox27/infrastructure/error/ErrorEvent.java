package sandbox27.infrastructure.error;

import org.springframework.lang.Nullable;
import sandbox27.infrastructure.security.SecUser;

public record ErrorEvent(Throwable t, String message, @Nullable SecUser user) {
}
