package gov.rajasthan.smart.srse.otp;

import org.springframework.stereotype.Component;

@Component
public class OtpSenderRouter {

    private final OtpProperties properties;
    private final LoggingOtpSender loggingOtpSender;
    private final SmtpOtpSender smtpOtpSender;
    private final SmsOtpSenderNotConfigured smsOtpSender;

    public OtpSenderRouter(
            OtpProperties properties,
            LoggingOtpSender loggingOtpSender,
            SmtpOtpSender smtpOtpSender,
            SmsOtpSenderNotConfigured smsOtpSender) {
        this.properties = properties;
        this.loggingOtpSender = loggingOtpSender;
        this.smtpOtpSender = smtpOtpSender;
        this.smsOtpSender = smsOtpSender;
    }

    public OtpSender emailSender() {
        return switch (properties.sender()) {
            case LOG -> loggingOtpSender;
            case SMTP -> smtpOtpSender;
            case SMS -> throw new IllegalStateException(
                    "srse.otp.sender=sms does not configure email — use SMTP or LOG for email OTP");
        };
    }

    public OtpSender smsSender() {
        return switch (properties.sender()) {
            case LOG -> loggingOtpSender;
            case SMTP -> smsOtpSender;
            case SMS -> smsOtpSender;
        };
    }
}
