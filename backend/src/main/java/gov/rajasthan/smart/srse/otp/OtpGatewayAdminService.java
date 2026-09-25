package gov.rajasthan.smart.srse.otp;

import gov.rajasthan.smart.srse.audit.AuditActionType;
import gov.rajasthan.smart.srse.audit.AuditOutcome;
import gov.rajasthan.smart.srse.audit.AuditService;
import gov.rajasthan.smart.srse.identity.AdminAuthorizationService;
import gov.rajasthan.smart.srse.identity.AppUser;
import gov.rajasthan.smart.srse.identity.AuthenticatedUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OtpGatewayAdminService {

    private static final String SMTP_TEST_BODY = """
            This is a test message from the Analytics Advisor admin OTP email gateway panel.
            If you received this, SMTP settings are working.""";

    private final AuthenticatedUserService authenticatedUserService;
    private final AdminAuthorizationService adminAuthorization;
    private final OtpSmtpSettings smtpSettings;
    private final OtpSmsSettings smsSettings;
    private final SmtpOtpSender smtpOtpSender;
    private final SmsOtpSenderNotConfigured smsOtpSender;
    private final AuditService auditService;

    public OtpGatewayAdminService(
            AuthenticatedUserService authenticatedUserService,
            AdminAuthorizationService adminAuthorization,
            OtpSmtpSettings smtpSettings,
            OtpSmsSettings smsSettings,
            SmtpOtpSender smtpOtpSender,
            SmsOtpSenderNotConfigured smsOtpSender,
            AuditService auditService) {
        this.authenticatedUserService = authenticatedUserService;
        this.adminAuthorization = adminAuthorization;
        this.smtpSettings = smtpSettings;
        this.smsSettings = smsSettings;
        this.smtpOtpSender = smtpOtpSender;
        this.smsOtpSender = smsOtpSender;
        this.auditService = auditService;
    }

    public SmtpGatewayView getSmtp() {
        assertSuperAdmin();
        return smtpSettings.activeConfig()
                .map(cfg -> new SmtpGatewayView(
                        cfg.host(),
                        cfg.port(),
                        cfg.username(),
                        cfg.fromAddress(),
                        cfg.tls(),
                        smtpSettings.passwordConfigured()))
                .orElse(new SmtpGatewayView(null, 587, null, null, true, smtpSettings.passwordConfigured()));
    }

    @Transactional
    public SmtpGatewayView saveSmtp(SmtpGatewayUpdateRequest request) {
        AppUser actor = assertSuperAdmin();
        if (request.host() == null || request.host().isBlank()) {
            throw new IllegalArgumentException("SMTP host is required");
        }
        smtpSettings.save(
                new OtpSmtpSettings.SmtpConfig(
                        request.host().trim(),
                        request.port() > 0 ? request.port() : 587,
                        blankToNull(request.username()),
                        request.password(),
                        blankToNull(request.fromAddress()),
                        request.tls()),
                true);
        auditService.recordGatewayEvent(
                AuditActionType.OTP_SMTP_GATEWAY_UPDATED,
                actor,
                AuditOutcome.SUCCESS,
                "host=" + request.host().trim());
        return getSmtp();
    }

    @Transactional
    public GatewayTestResult testSmtp() {
        AppUser actor = assertSuperAdmin();
        if (!actor.isEmailVerified()) {
            throw new IllegalArgumentException("Your account has no verified email for a test message");
        }
        String email = actor.getEmail();
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Your account has no verified email for a test message");
        }
        OtpSendResult result = smtpOtpSender.send(OtpChannel.EMAIL, email.trim(), SMTP_TEST_BODY);
        auditService.recordGatewayEvent(
                AuditActionType.OTP_SMTP_GATEWAY_TEST,
                actor,
                result.success() ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE,
                result.detail());
        return new GatewayTestResult(result.success(), result.detail());
    }

    public SmsGatewayView getSms() {
        assertSuperAdmin();
        return smsSettings.activeConfig()
                .map(cfg -> new SmsGatewayView(
                        cfg.endpoint(),
                        cfg.username(),
                        cfg.senderId(),
                        smsSettings.passwordConfigured()))
                .orElse(new SmsGatewayView(null, null, null, smsSettings.passwordConfigured()));
    }

    @Transactional
    public SmsGatewayView saveSms(SmsGatewayUpdateRequest request) {
        AppUser actor = assertSuperAdmin();
        if (request.endpoint() == null || request.endpoint().isBlank()) {
            throw new IllegalArgumentException("SMS endpoint is required");
        }
        smsSettings.save(
                new OtpSmsSettings.SmsConfig(
                        request.endpoint().trim(),
                        blankToNull(request.username()),
                        request.password(),
                        blankToNull(request.senderId())),
                true);
        auditService.recordGatewayEvent(
                AuditActionType.OTP_SMS_GATEWAY_UPDATED,
                actor,
                AuditOutcome.SUCCESS,
                "endpoint=" + request.endpoint().trim());
        return getSms();
    }

    @Transactional
    public GatewayTestResult testSms() {
        AppUser actor = assertSuperAdmin();
        String destination = actor.isMobileVerified() && actor.getMobile() != null && !actor.getMobile().isBlank()
                ? actor.getMobile().trim()
                : "0000000000";
        OtpSendResult result = smsOtpSender.send(OtpChannel.SMS, destination, "SRSE SMS gateway test");
        auditService.recordGatewayEvent(
                AuditActionType.OTP_SMS_GATEWAY_TEST,
                actor,
                AuditOutcome.FAILURE,
                result.detail());
        return new GatewayTestResult(result.success(), result.detail());
    }

    private AppUser assertSuperAdmin() {
        AppUser user = authenticatedUserService.requireCurrentUser();
        adminAuthorization.assertSuperAdminOnly(user);
        return user;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record SmtpGatewayView(
            String host,
            int port,
            String username,
            String fromAddress,
            boolean tls,
            boolean passwordConfigured) {
    }

    public record SmtpGatewayUpdateRequest(
            String host,
            int port,
            String username,
            String password,
            String fromAddress,
            boolean tls) {
    }

    public record SmsGatewayView(
            String endpoint,
            String username,
            String senderId,
            boolean passwordConfigured) {
    }

    public record SmsGatewayUpdateRequest(
            String endpoint,
            String username,
            String password,
            String senderId) {
    }

    public record GatewayTestResult(boolean success, String message) {
    }
}
