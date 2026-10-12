package gov.rajasthan.smart.srse.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PRODUCT_PLAN 10.B.1 — session lifetime and idle timeout are configuration, not constants. */
class SessionPolicyTest {

    @Test
    void defaultsMatchDecisionA14() {
        SessionProperties defaults = SessionProperties.defaults();
        assertEquals(Duration.ofHours(8), defaults.absoluteLifetime());
        assertEquals(Duration.ofMinutes(30), defaults.idleTimeout());
        assertTrue(defaults.idleTimeoutEnabled());
    }

    @Test
    void zeroIdleTimeoutSwitchesTheIdleCheckOff() {
        assertFalse(new SessionProperties(null, Duration.ZERO, null).idleTimeoutEnabled());
    }

    @Test
    void rejectsNonsensePolicyAtStartup() {
        assertThrows(IllegalArgumentException.class,
                () -> new SessionProperties(Duration.ZERO, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new SessionProperties(null, Duration.ofMinutes(-1), null));
        // A write interval at or beyond the idle timeout would sign active users out.
        assertThrows(IllegalArgumentException.class,
                () -> new SessionProperties(null, Duration.ofMinutes(1), Duration.ofMinutes(1)));
    }

    @Test
    void tokenLifetimeFollowsConfiguration() {
        SessionTokenService shortLived = new SessionTokenService(
                new SessionProperties(Duration.ofMinutes(90), null, null));
        String token = shortLived.issue("u", List.of("A"), 0L, false);

        io.jsonwebtoken.Claims claims = io.jsonwebtoken.Jwts.parserBuilder()
                .setSigningKey((javax.crypto.SecretKey) org.springframework.test.util.ReflectionTestUtils
                        .getField(shortLived, "signingKey"))
                .build().parseClaimsJws(token).getBody();
        long seconds = Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant())
                .getSeconds();
        assertEquals(Duration.ofMinutes(90).getSeconds(), seconds);
        assertTrue(claims.getExpiration().toInstant().isBefore(Instant.now().plus(Duration.ofHours(2))));
    }
}
