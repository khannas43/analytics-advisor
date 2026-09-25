package gov.rajasthan.smart.srse.otp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "srse.otp")
public record OtpProperties(
        Sender sender,
        int codeTtlMinutes,
        int maxVerifyAttempts,
        int resendCooldownSeconds) {

    public OtpProperties {
        if (codeTtlMinutes <= 0) {
            codeTtlMinutes = 5;
        }
        if (maxVerifyAttempts <= 0) {
            maxVerifyAttempts = 5;
        }
        if (resendCooldownSeconds <= 0) {
            resendCooldownSeconds = 60;
        }
        if (sender == null) {
            sender = Sender.LOG;
        }
    }

    public enum Sender {
        LOG,
        SMTP,
        SMS
    }
}
