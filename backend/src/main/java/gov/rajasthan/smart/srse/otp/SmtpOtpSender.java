package gov.rajasthan.smart.srse.otp;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
public class SmtpOtpSender implements OtpSender {

    private final OtpSmtpSettings smtpSettings;

    public SmtpOtpSender(OtpSmtpSettings smtpSettings) {
        this.smtpSettings = smtpSettings;
    }

    @Override
    public OtpSendResult send(OtpChannel channel, String destination, String plainTextBody) {
        if (channel != OtpChannel.EMAIL) {
            return new OtpSendResult(channel, false, "SMTP sender handles email only");
        }
        OtpSmtpSettings.SmtpConfig config = smtpSettings.activeConfig()
                .orElseThrow(() -> new IllegalStateException("SMTP OTP is not configured"));
        try {
            JavaMailSenderImpl mailSender = buildSender(config);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(config.fromAddress() != null ? config.fromAddress() : config.username());
            message.setTo(destination);
            message.setSubject("SRSE verification code");
            message.setText(plainTextBody);
            mailSender.send(message);
            return new OtpSendResult(channel, true, "sent");
        } catch (RuntimeException ex) {
            return new OtpSendResult(channel, false, ex.getClass().getSimpleName());
        }
    }

    private static JavaMailSenderImpl buildSender(OtpSmtpSettings.SmtpConfig config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.host());
        sender.setPort(config.port());
        if (config.username() != null) {
            sender.setUsername(config.username());
        }
        if (config.password() != null) {
            sender.setPassword(config.password());
        }
        Properties javaMail = sender.getJavaMailProperties();
        javaMail.put("mail.smtp.auth", config.username() != null);
        javaMail.put("mail.smtp.starttls.enable", String.valueOf(config.tls()));
        return sender;
    }
}
