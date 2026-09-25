package gov.rajasthan.smart.srse.otp;

import gov.rajasthan.smart.srse.identity.AppUser;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** One code, both channels (§7.1a Part 2). */
@Service
public class OtpDeliveryService {

    private final OtpSenderRouter senderRouter;

    public OtpDeliveryService(OtpSenderRouter senderRouter) {
        this.senderRouter = senderRouter;
    }

    public DeliveryOutcome deliverLoginCode(AppUser user, String code) {
        String body = "Your SRSE verification code is: " + code + ". It expires in five minutes.";
        return deliverToVerifiedContacts(user, body);
    }

    public DeliveryOutcome deliverVerificationCode(AppUser user, OtpChannel channel, String code) {
        String body = "Your SRSE contact verification code is: " + code + ". It expires in five minutes.";
        String destination = channel == OtpChannel.EMAIL ? user.getEmail() : user.getMobile();
        if (destination == null || destination.isBlank()) {
            return new DeliveryOutcome(List.of(new OtpSendResult(channel, false, "missing contact")), false);
        }
        OtpSender sender = channel == OtpChannel.EMAIL ? senderRouter.emailSender() : senderRouter.smsSender();
        OtpSendResult result = sender.send(channel, destination, body);
        return new DeliveryOutcome(List.of(result), result.success());
    }

    private DeliveryOutcome deliverToVerifiedContacts(AppUser user, String body) {
        List<OtpSendResult> results = new ArrayList<>();
        if (user.isEmailVerified() && user.getEmail() != null && !user.getEmail().isBlank()) {
            results.add(senderRouter.emailSender().send(OtpChannel.EMAIL, user.getEmail(), body));
        }
        if (user.isMobileVerified() && user.getMobile() != null && !user.getMobile().isBlank()) {
            results.add(senderRouter.smsSender().send(OtpChannel.SMS, user.getMobile(), body));
        }
        boolean anySuccess = results.stream().anyMatch(OtpSendResult::success);
        return new DeliveryOutcome(results, anySuccess);
    }

    public record DeliveryOutcome(List<OtpSendResult> results, boolean anyChannelSucceeded) {

        public String statusSummary() {
            return results.stream()
                    .map(r -> r.channel().name() + ":" + (r.success() ? "OK" : "FAIL"))
                    .reduce((a, b) -> a + "," + b)
                    .orElse("NONE");
        }
    }
}
