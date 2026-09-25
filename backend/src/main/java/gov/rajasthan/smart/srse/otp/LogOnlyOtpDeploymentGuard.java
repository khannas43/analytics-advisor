package gov.rajasthan.smart.srse.otp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Refuses to start with log-only OTP outside an explicit local profile (§7.1a.8).
 */
@Component
public class LogOnlyOtpDeploymentGuard {

    private final OtpProperties otpProperties;
    private final Environment environment;
    private final String authMode;

    public LogOnlyOtpDeploymentGuard(
            OtpProperties otpProperties,
            Environment environment,
            @Value("${srse.auth-mode:mock}") String authMode) {
        this.otpProperties = otpProperties;
        this.environment = environment;
        this.authMode = authMode;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refuseLogOnlyOtpInNonLocalDeployment() {
        if (otpProperties.sender() != OtpProperties.Sender.LOG) {
            return;
        }
        if (!"local".equalsIgnoreCase(authMode)) {
            return;
        }
        boolean localProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "local".equalsIgnoreCase(p));
        if (!localProfile && environment.getActiveProfiles().length > 0) {
            throw new IllegalStateException(
                    "srse.otp.sender=log is only permitted with Spring profile 'local' "
                            + "when srse.auth-mode=local — configure SMTP or SMS for this deployment");
        }
    }
}
