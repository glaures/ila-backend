package sandbox27.ila.infrastructure.security;

public record CurrentUser(String sub, String email, String role) {}