package gov.rajasthan.smart.srse.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Session policy (decision A14): 8-hour absolute lifetime, 30-minute idle timeout.
 * Government deployments often mandate other values, so both are configuration.
 *
 * <p>{@code idleTimeout} of zero switches the idle check off. Activity is written at
 * most once per {@code activityWriteInterval} per user, so the effective idle window
 * is {@code idleTimeout} minus up to one interval.
 */
@ConfigurationProperties(prefix = "srse.session")
public record SessionProperties(
        Duration absoluteLifetime,
        Duration idleTimeout,
        Duration activityWriteInterval) {

    public SessionProperties {
        absoluteLifetime = absoluteLifetime == null ? Duration.ofHours(8) : absoluteLifetime;
        idleTimeout = idleTimeout == null ? Duration.ofMinutes(30) : idleTimeout;
        activityWriteInterval = activityWriteInterval == null ? Duration.ofMinutes(1) : activityWriteInterval;
        if (absoluteLifetime.isZero() || absoluteLifetime.isNegative()) {
            throw new IllegalArgumentException("srse.session.absolute-lifetime must be positive");
        }
        if (idleTimeout.isNegative()) {
            throw new IllegalArgumentException("srse.session.idle-timeout must not be negative");
        }
        if (activityWriteInterval.isZero() || activityWriteInterval.isNegative()) {
            throw new IllegalArgumentException("srse.session.activity-write-interval must be positive");
        }
        if (!idleTimeout.isZero() && activityWriteInterval.compareTo(idleTimeout) >= 0) {
            throw new IllegalArgumentException(
                    "srse.session.activity-write-interval must be shorter than srse.session.idle-timeout");
        }
    }

    public static SessionProperties defaults() {
        return new SessionProperties(null, null, null);
    }

    public boolean idleTimeoutEnabled() {
        return !idleTimeout.isZero();
    }
}
