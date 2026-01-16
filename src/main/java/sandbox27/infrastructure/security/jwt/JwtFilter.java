package sandbox27.infrastructure.security.jwt;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtFilter extends OncePerRequestFilter {

    private final JwtValidator jwtValidator;

    public JwtFilter(JwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws IOException, ServletException {

        if (!request.getRequestURI().startsWith("/auth")
                && !request.getRequestURI().startsWith("/login")
                && !request.getRequestURI().endsWith("/password-reset")
                && !request.getMethod().equals("OPTIONS")
        ) {
            String auth = request.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Kein Token");
                return;
            }
            try {
                jwtValidator.validateJwt(auth.substring(7));
            } catch (JwtException e) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Ungültiges Token");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
