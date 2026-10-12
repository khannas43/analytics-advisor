package gov.rajasthan.smart.srse.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * Issues and verifies bearer tokens for {@code mock} and {@code local} auth modes.
 * Signing key is generated per boot (acceptable for non-production modes).
 */
@Component
@EnableConfigurationProperties(SessionProperties.class)
public class SessionTokenService {

    public static final String CLAIM_AUTHORITIES = "authorities";
    public static final String CLAIM_SESSION_VERSION = "sessionVersion";
    public static final String CLAIM_MUST_CHANGE_PASSWORD = "mustChangePassword";

    private static final Logger log = LoggerFactory.getLogger(SessionTokenService.class);
    /** HS256 needs at least 256 bits; a shorter secret is brute-forceable and jjwt refuses it anyway. */
    static final int MIN_KEY_BYTES = 32;

    private final SecretKey signingKey;
    private final boolean keyConfigured;
    private final Duration tokenTtl;

    /** Default policy, random per-boot key — for tests that do not care. */
    public SessionTokenService() {
        this(SessionProperties.defaults(), null);
    }

    /** Random per-boot key. Sessions do not survive a restart. */
    public SessionTokenService(SessionProperties properties) {
        this(properties, null);
    }

    /**
     * @param encodedSigningKey Base64 secret of at least 32 bytes ({@code SRSE_SESSION_SIGNING_KEY}),
     *     so tokens survive a restart and validate on every instance. Blank means a random key
     *     generated per boot — acceptable for {@code mock} mode only; {@code local} mode refuses to
     *     start without one ({@link SessionSigningKeyStartupValidator}).
     */
    @Autowired
    public SessionTokenService(
            SessionProperties properties,
            @Value("${srse.session.signing-key:}") String encodedSigningKey) {
        this.tokenTtl = properties.absoluteLifetime();
        if (encodedSigningKey == null || encodedSigningKey.isBlank()) {
            this.signingKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
            this.keyConfigured = false;
            log.warn("No srse.session.signing-key configured: session tokens use a random per-boot key, "
                    + "so every restart signs all users out and a second instance cannot validate them.");
        } else {
            this.signingKey = parseKey(encodedSigningKey);
            this.keyConfigured = true;
        }
    }

    /** True when a stable key was supplied, i.e. sessions survive restarts. */
    public boolean signingKeyConfigured() {
        return keyConfigured;
    }

    /** Never echoes the value: it is a secret, and an error message ends up in logs. */
    static SecretKey parseKey(String encoded) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "srse.session.signing-key must be Base64 (generate one with: openssl rand -base64 48)");
        }
        if (decoded.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "srse.session.signing-key must decode to at least " + MIN_KEY_BYTES
                            + " bytes (generate one with: openssl rand -base64 48)");
        }
        return Keys.hmacShaKeyFor(decoded);
    }

    public String issue(String subject, List<String> authorities, long sessionVersion, boolean mustChangePassword) {
        Instant now = Instant.now();
        Instant expires = now.plus(tokenTtl);
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
