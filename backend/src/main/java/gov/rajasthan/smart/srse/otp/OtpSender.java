package gov.rajasthan.smart.srse.otp;

/**
 * Delivers a one-time code to one channel (§7.1a.8). Implementations must never
 * log the code except {@link LoggingOtpSender}.
 */
public interface OtpSender {

    OtpSendResult send(OtpChannel channel, String destination, String plainTextBody);
}
