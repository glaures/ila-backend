package sandbox27.infrastructure.security.jwt;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class AuthorizationTokenReader {

    final JwtValidator jwtValidator;

    public String retrievePrincipalFromAuthorizationHeader(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Kein Token");
        }

        String token = authorizationHeader.substring(7);

        Claims claims = jwtValidator.validateJwt(token);
        return claims.getSubject();
    }

}
