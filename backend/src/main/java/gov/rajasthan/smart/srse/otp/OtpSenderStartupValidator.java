package gov.rajasthan.smart.srse.otp;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class OtpSenderStartupValidator {

    private final OtpProperties properties;

    public OtpSenderStartupValidator(OtpProperties properties) {
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refuseSmsUntilGatewayExists() {
        if (properties.sender() == OtpProperties.Sender.SMS) {
            throw new IllegalStateException(
                    "srse.otp.sender=sms is not available until the SMS gateway contract is "
                            + "implemented (7.1a.7). Use log or smtp.");
        }
    }
}
