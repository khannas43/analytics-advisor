package gov.rajasthan.smart.srse.datasource;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalDataSourceSecretCodecTest {

    private static String key(byte value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, value);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void encryptsWithRandomIvAndRoundTrips() {
        ExternalDataSourceSecretCodec codec = new ExternalDataSourceSecretCodec(key((byte) 7));
        String first = codec.encrypt("not-plain-text");
        String second = codec.encrypt("not-plain-text");

        assertTrue(first.startsWith("v1:"));
        assertNotEquals(first, second);
        assertFalse(first.contains("not-plain-text"));
        assertEquals("not-plain-text", codec.decrypt(first));
        assertEquals("not-plain-text", codec.decrypt(second));
    }

    @Test
    void refusesPersistenceWhenDeploymentKeyIsMissing() {
        ExternalDataSourceSecretCodec codec = new ExternalDataSourceSecretCodec("");
        assertFalse(codec.configured());
        assertThrows(IllegalStateException.class, () -> codec.encrypt("secret"));
    }

    @Test
    void wrongKeyCannotDecryptEnvelope() {
        String encrypted = new ExternalDataSourceSecretCodec(key((byte) 1)).encrypt("secret");
        ExternalDataSourceSecretCodec other = new ExternalDataSourceSecretCodec(key((byte) 2));
        assertThrows(IllegalStateException.class, () -> other.decrypt(encrypted));
    }
}
