package sandbox27.infrastructure.security.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtGenerator {

    /**
     * Claim mit dem Principal des Admins, der die Sitzung übernommen hat. Nur in
     * Impersonation-Tokens gesetzt; das Subject bleibt der übernommene Nutzer, damit
     * die gesamte Autorisierung unverändert an dessen Rollen hängt.
     */
    public final static String IMPERSONATOR_CLAIM = "impersonator";

    /**
     * Impersonation-Tokens laufen bewusst ab – anders als reguläre Login-Tokens, die
     * unbegrenzt gültig sind. Ein Token, mit dem man in einer fremden Identität
     * unterwegs ist, soll nicht dauerhaft in einem Browser liegen bleiben.
     */
    private final static Duration IMPERSONATION_TTL = Duration.ofHours(1);

    @Value("${jwt.secret}")
    private String jwtSecret;

    public String createToken(String userId) {
        return Jwts.builder()
                .subject(userId)
                .issuedAt(new Date())
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String createImpersonationToken(String userId, String impersonator) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .claim(IMPERSONATOR_CLAIM, impersonator)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(IMPERSONATION_TTL)))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtSecret));
    }
}
