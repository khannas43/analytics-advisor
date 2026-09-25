package gov.rajasthan.smart.srse.otp;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class OtpCodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Six-digit code, leading zeros preserved. */
    public String generateSixDigitCode() {
        int value = RANDOM.nextInt(1_000_000);
        return String.format("%06d", value);
    }
}
