package gov.rajasthan.smart.srse.otp;

public record OtpSendResult(OtpChannel channel, boolean success, String detail) {
}
