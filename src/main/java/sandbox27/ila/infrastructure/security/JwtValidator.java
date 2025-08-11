package sandbox27.ila.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sandbox27.ila.backend.user.UserRepository;

import javax.crypto.SecretKey;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class JwtValidator {

    @Value("${jwt.secret}")
    private String jwtSecret;
    private final UserRepository userRepository;

    public Claims validateJwt(String jwt) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtSecret));
        Jws<Claims> parsedJwt = Jwts
                .parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(jwt);
        if(!userRepository.existsById(parsedJwt.getPayload().getSubject()))
            throw new JwtException("no user with id " + parsedJwt.getPayload().getSubject() + " found.");
        return parsedJwt.getPayload();
    }
}
