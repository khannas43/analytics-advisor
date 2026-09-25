package gov.rajasthan.smart.srse.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Local development sender — the only implementation permitted to log digits.
 */
@Component
public class LoggingOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingOtpSender.class);

    @Override
    public OtpSendResult send(OtpChannel channel, String destination, String plainTextBody) {
        log.info("OTP [{}] to {} — {}", channel, maskDestination(channel, destination), plainTextBody);
        return new OtpSendResult(channel, true, "logged");
    }

    private static String maskDestination(OtpChannel channel, String destination) {
        return channel == OtpChannel.EMAIL
                ? ContactMasking.maskEmail(destination)
                : ContactMasking.maskMobile(destination);
    }
}
