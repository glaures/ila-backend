package sandbox27.ila.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;

@Component
public class JwtValidator {

    @Value("${jwt.secret}")
    private String jwtSecret;

    public Claims validateJwt(String jwt) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtSecret));
        Jws<Claims> parsedJwt = Jwts
                .parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(jwt);
        return parsedJwt.getPayload();
    }
}
