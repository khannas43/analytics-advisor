package gov.rajasthan.smart.srse.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Issues and verifies bearer tokens for {@code mock} and {@code local} auth modes.
 * Signing key is generated per boot (acceptable for non-production modes).
 */
@Component
@ConditionalOnExpression("'${srse.auth-mode:mock}' == 'mock' || '${srse.auth-mode:mock}' == 'local'")
public class SessionTokenService {

    public static final String CLAIM_AUTHORITIES = "authorities";
    public static final String CLAIM_SESSION_VERSION = "sessionVersion";
    public static final String CLAIM_MUST_CHANGE_PASSWORD = "mustChangePassword";

    private static final Duration TOKEN_TTL = Duration.ofHours(8);

    private final SecretKey signingKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);

    public String issue(String subject, List<String> authorities, long sessionVersion, boolean mustChangePassword) {
        Instant now = Instant.now();
        Instant expires = now.plus(TOKEN_TTL);
        return Jwts.builder()
                .setSubject(subject)
                .claim(CLAIM_AUTHORITIES, List.copyOf(authorities))
                .claim(CLAIM_SESSION_VERSION, sessionVersion)
                .claim(CLAIM_MUST_CHANGE_PASSWORD, mustChangePassword)
                .setIssuedAt(toLegacyDate(now))
                .setExpiration(toLegacyDate(expires))
                .signWith(signingKey)
                .compact();
    }

    public ParsedSessionToken parse(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        @SuppressWarnings("unchecked")
        List<String> authorities = (List<String>) claims.get(CLAIM_AUTHORITIES, List.class);
        Number sessionVersion = claims.get(CLAIM_SESSION_VERSION, Number.class);
        Boolean mustChange = claims.get(CLAIM_MUST_CHANGE_PASSWORD, Boolean.class);
        return new ParsedSessionToken(
                claims.getSubject(),
                authorities == null ? List.of() : List.copyOf(authorities),
                sessionVersion == null ? 0L : sessionVersion.longValue(),
                Boolean.TRUE.equals(mustChange));
    }

    @SuppressWarnings("java:S2143")
    private static java.util.Date toLegacyDate(Instant instant) {
        return java.util.Date.from(instant);
    }

    public record ParsedSessionToken(
            String username,
            List<String> authorities,
            long sessionVersion,
            boolean mustChangePassword) {
    }
}
