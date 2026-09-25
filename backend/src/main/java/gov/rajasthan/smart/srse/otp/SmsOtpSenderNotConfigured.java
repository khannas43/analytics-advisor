package gov.rajasthan.smart.srse.otp;

import org.springframework.stereotype.Component;

/** Placeholder until the SMS gateway contract exists (7.1a.7). */
@Component
public class SmsOtpSenderNotConfigured implements OtpSender {

    @Override
    public OtpSendResult send(OtpChannel channel, String destination, String plainTextBody) {
        return new OtpSendResult(channel, false, "SMS gateway is not configured (7.1a.7)");
    }
}
