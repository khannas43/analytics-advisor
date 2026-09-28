package gov.rajasthan.smart.srse.datasource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-GCM envelope encryption for stored source credentials. The key never enters the database. */
@Component
public class ExternalDataSourceSecretCodec {

    private static final String VERSION = "v1";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public ExternalDataSourceSecretCodec(
            @Value("${srse.external-sources.secret-key:}") String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            this.key = null;
            return;
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedKey.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("External data-source secret key must be base64", ex);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("External data-source secret key must decode to 32 bytes");
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    public boolean configured() {
        return key != null;
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        SecretKey activeKey = requireKey();
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, activeKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return VERSION + ":" + Base64.getEncoder().encodeToString(iv) + ":"
                    + Base64.getEncoder().encodeToString(encrypted);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not encrypt data-source password", ex);
        }
    }

    public String decrypt(String envelope) {
        SecretKey activeKey = requireKey();
        String[] parts = envelope == null ? new String[0] : envelope.split(":", 3);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new IllegalStateException("Unsupported encrypted data-source password format");
        }
        try {
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] encrypted = Base64.getDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, activeKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Could not decrypt data-source password", ex);
        }
    }

    private SecretKey requireKey() {
        if (key == null) {
            throw new IllegalStateException(
                    "External data sources are disabled until SRSE_EXTERNAL_SOURCE_SECRET_KEY is configured");
        }
        return key;
    }
}
