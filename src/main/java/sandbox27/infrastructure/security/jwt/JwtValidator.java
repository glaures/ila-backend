package sandbox27.infrastructure.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sandbox27.infrastructure.security.UserManagement;

import javax.crypto.SecretKey;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class JwtValidator {

    @Value("${jwt.secret}")
    private String jwtSecret;
    private final UserManagement userManagement;

    public Claims validateJwt(String jwt) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtSecret));
        Jws<Claims> parsedJwt = Jwts
                .parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(jwt);
        if(userManagement.findUserByPrincipal(parsedJwt.getPayload().getSubject()).isEmpty())
            throw new JwtException("no user with id " + parsedJwt.getPayload().getSubject() + " found.");
        return parsedJwt.getPayload();
    }
}
