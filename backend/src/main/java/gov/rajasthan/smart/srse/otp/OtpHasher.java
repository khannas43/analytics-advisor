package gov.rajasthan.smart.srse.otp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Short-lived OTP storage — hash only, never persist digits. */
public final class OtpHasher {

    private OtpHasher() {
    }

    public static String hash(long userId, OtpPurpose purpose, String sixDigitCode) {
        String payload = userId + ":" + purpose.name() + ":" + sixDigitCode;
        return sha256Hex(payload);
    }

    public static boolean matches(long userId, OtpPurpose purpose, String sixDigitCode, String storedHash) {
        return hash(userId, purpose, sixDigitCode).equals(storedHash);
    }

    private static String sha256Hex(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
