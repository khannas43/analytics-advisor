package gov.rajasthan.smart.srse.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "srse.identity")
public record IdentityProperties(
        Lockout lockout,
        Password password,
        Bootstrap bootstrap) {

    public record Lockout(int maxFailedAttempts, int lockDurationMinutes) {
    }

    public record Password(int expiryDays) {
    }

    public record Bootstrap(String superAdminUsername) {
    }
}
