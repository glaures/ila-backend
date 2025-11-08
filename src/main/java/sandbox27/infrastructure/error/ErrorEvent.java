package sandbox27.infrastructure.error;

public record ErrorEvent(Throwable t, String message) {
}
