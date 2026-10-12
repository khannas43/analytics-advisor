package gov.rajasthan.smart.srse.security;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A stable signing key (sessions survive a restart and validate on every instance). */
class SessionSigningKeyTest {

    private static String newKey(int bytes) {
        byte[] raw = new byte[bytes];
        new SecureRandom().nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }

    private static SessionTokenService serviceWith(String key) {
        return new SessionTokenService(SessionProperties.defaults(), key);
    }

    @Test
    void aTokenSurvivesARestartWhenTheKeyIsConfigured() {
        String key = newKey(48);
        String token = serviceWith(key).issue("officer", List.of("A"), 3L, false);

        // a second instance with the same key == the same app after a restart, or a second replica
        SessionTokenService.ParsedSessionToken parsed = serviceWith(key).parse(token);

        assertEquals("officer", parsed.username());
        assertEquals(3L, parsed.sessionVersion());
    }

    @Test
    void aDifferentKeyRejectsTheToken() {
        String token = serviceWith(newKey(48)).issue("officer", List.of("A"), 0L, false);
        assertThrows(RuntimeException.class, () -> serviceWith(newKey(48)).parse(token));
    }

    @Test
    void withoutAKeyEachBootGetsItsOwnAndTokensDoNotCarryOver() {
        SessionTokenService first = serviceWith(null);
        SessionTokenService second = serviceWith("  ");
        assertFalse(first.signingKeyConfigured());
        String token = first.issue("officer", List.of("A"), 0L, false);
        assertThrows(RuntimeException.class, () -> second.parse(token));
    }

    @Test
    void aShortKeyIsRefusedWithoutEchoingIt() {
        String tooShort = newKey(16);
        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> serviceWith(tooShort));
        assertTrue(failure.getMessage().contains("at least 32 bytes"), failure.getMessage());
        assertFalse(failure.getMessage().contains(tooShort));
    }

    @Test
    void aKeyThatIsNotBase64IsRefusedWithoutEchoingIt() {
        String notBase64 = "this is *not* base64 !!";
        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> serviceWith(notBase64));
        assertTrue(failure.getMessage().contains("Base64"), failure.getMessage());
        assertFalse(failure.getMessage().contains(notBase64));
    }

    @Test
    void localModeRefusesToStartWithoutAKey() {
        SessionSigningKeyStartupValidator withoutKey =
                new SessionSigningKeyStartupValidator(serviceWith(null));
        IllegalStateException failure =
                assertThrows(IllegalStateException.class, withoutKey::afterPropertiesSet);
        assertTrue(failure.getMessage().contains("SRSE_SESSION_SIGNING_KEY"), failure.getMessage());
    }

    @Test
    void localModeStartsWithAKey() {
        SessionSigningKeyStartupValidator withKey =
                new SessionSigningKeyStartupValidator(serviceWith(newKey(48)));
        assertDoesNotThrow(withKey::afterPropertiesSet);
    }
}
