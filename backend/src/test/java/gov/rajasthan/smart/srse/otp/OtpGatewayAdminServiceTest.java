package gov.rajasthan.smart.srse.otp;

import gov.rajasthan.smart.srse.audit.AuditActionType;
import gov.rajasthan.smart.srse.audit.AuditOutcome;
import gov.rajasthan.smart.srse.audit.AuditService;
import gov.rajasthan.smart.srse.identity.AdminAccessDeniedException;
import gov.rajasthan.smart.srse.identity.AdminAuthorizationService;
import gov.rajasthan.smart.srse.identity.AppUser;
import gov.rajasthan.smart.srse.identity.AuthenticatedUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpGatewayAdminServiceTest {

    @Mock
    private AuthenticatedUserService authenticatedUserService;
    @Mock
    private AdminAuthorizationService adminAuthorization;
    @Mock
    private OtpSmtpSettings smtpSettings;
    @Mock
    private OtpSmsSettings smsSettings;
    @Mock
    private SmtpOtpSender smtpOtpSender;
    @Mock
    private SmsOtpSenderNotConfigured smsOtpSender;
    @Mock
    private AuditService auditService;

    private OtpGatewayAdminService service;
    private AppUser superAdmin;

    @BeforeEach
    void setUp() {
        service = new OtpGatewayAdminService(
                authenticatedUserService,
                adminAuthorization,
                smtpSettings,
                smsSettings,
                smtpOtpSender,
                smsOtpSender,
                auditService);
        superAdmin = new AppUser("superadmin", "hash");
        superAdmin.setEmail("admin@example.com");
        superAdmin.setEmailVerified(true);
    }

    @Test
    void nonSuperAdminIsRefusedOnSmsGet() {
        when(authenticatedUserService.requireCurrentUser()).thenReturn(superAdmin);
        doThrow(new AdminAccessDeniedException("SuperAdmin only"))
                .when(adminAuthorization).assertSuperAdminOnly(superAdmin);

        assertThrows(AdminAccessDeniedException.class, () -> service.getSms());
    }

    @Test
    void nonSuperAdminIsRefusedOnSmtpGet() {
        when(authenticatedUserService.requireCurrentUser()).thenReturn(superAdmin);
        doThrow(new AdminAccessDeniedException("SuperAdmin only"))
                .when(adminAuthorization).assertSuperAdminOnly(superAdmin);

        assertThrows(AdminAccessDeniedException.class, () -> service.getSmtp());
    }

    @Test
    void saveSmtpWritesAuditRow() {
        when(authenticatedUserService.requireCurrentUser()).thenReturn(superAdmin);
        when(smtpSettings.passwordConfigured()).thenReturn(true);
        when(smtpSettings.activeConfig()).thenReturn(Optional.of(
                new OtpSmtpSettings.SmtpConfig("smtp.test", 587, "u", "pw", "from@test", true)));

        service.saveSmtp(new OtpGatewayAdminService.SmtpGatewayUpdateRequest(
                "smtp.test", 587, "u", "pw", "from@test", true));

        verify(smtpSettings).save(any(), eq(true));
        verify(auditService).recordGatewayEvent(
                eq(AuditActionType.OTP_SMTP_GATEWAY_UPDATED),
                eq(superAdmin),
                eq(AuditOutcome.SUCCESS),
                eq("host=smtp.test"));
    }

    @Test
    void saveSmsWritesAuditRow() {
        when(authenticatedUserService.requireCurrentUser()).thenReturn(superAdmin);
        when(smsSettings.passwordConfigured()).thenReturn(false);
        when(smsSettings.activeConfig()).thenReturn(Optional.of(
                new OtpSmsSettings.SmsConfig("https://sms.example/send", "u", "pw", "SRSE")));

        service.saveSms(new OtpGatewayAdminService.SmsGatewayUpdateRequest(
                "https://sms.example/send", "u", "pw", "SRSE"));

        verify(smsSettings).save(any(), eq(true));
        verify(auditService).recordGatewayEvent(
                eq(AuditActionType.OTP_SMS_GATEWAY_UPDATED),
                eq(superAdmin),
                eq(AuditOutcome.SUCCESS),
                eq("endpoint=https://sms.example/send"));
    }

    @Test
    void smsTestReportsNotConfigured() {
        when(authenticatedUserService.requireCurrentUser()).thenReturn(superAdmin);
        when(smsOtpSender.send(OtpChannel.SMS, "0000000000", "SRSE SMS gateway test"))
                .thenReturn(new OtpSendResult(OtpChannel.SMS, false, "SMS gateway is not configured (7.1a.7)"));

        OtpGatewayAdminService.GatewayTestResult result = service.testSms();

        assertFalse(result.success());
        assertEquals("SMS gateway is not configured (7.1a.7)", result.message());
        verify(auditService).recordGatewayEvent(
                eq(AuditActionType.OTP_SMS_GATEWAY_TEST),
                eq(superAdmin),
                eq(AuditOutcome.FAILURE),
                eq("SMS gateway is not configured (7.1a.7)"));
    }
}
