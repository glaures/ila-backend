package sandbox27.ila.backend.user.events;

public record UserPasswordResetEvent(
        String login,
        String firstName,
        String email,
        String password) {
}
