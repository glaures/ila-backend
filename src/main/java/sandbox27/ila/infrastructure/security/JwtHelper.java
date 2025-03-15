package sandbox27.ila.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class JwtHelper {

    private SecretKey secretKey;

    public JwtHelper(PersistedKeyRepository persistedKeyRepository) {
        this.secretKey = persistedKeyRepository.findById(PersistedKey.ID)
                .orElseGet(() -> {
                    Key key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
                    PersistedKey persistedKey = PersistedKey.builder()
                            .id(PersistedKey.ID)
                            .algorithm(key.getAlgorithm())
                            .format(key.getFormat())
                            .encoded(key.getEncoded())
                            .build();
                    return persistedKeyRepository.save(persistedKey);
                });
    }

    public String generateToken(String email) {
        var now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(SecurityConstants.EXPIRATION_TIME_IN_DAYS, ChronoUnit.DAYS)))
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
    }

    public String extractUsername(String token) throws UnauthorizedException {
        return getTokenBody(token).getSubject();
    }

    public Boolean validateToken(String token, UserDetails userDetails) throws UnauthorizedException {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private Claims getTokenBody(String token) throws UnauthorizedException {
        try {
            return Jwts
                    .parser()
                    .setSigningKey(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (SignatureException | ExpiredJwtException |
                 MalformedJwtException e) { // Invalid signature or expired token
            throw new UnauthorizedException(e);
        }
    }

    private boolean isTokenExpired(String token) throws UnauthorizedException {
        Claims claims = getTokenBody(token);
        return claims.getExpiration().before(new Date());
    }
}