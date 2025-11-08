package sandbox27.ila.backend.user.events;

public record UserCreatedEvent(
        String login,
        String firstName,
        String lastName,
        String email,
        String password
) {
}
