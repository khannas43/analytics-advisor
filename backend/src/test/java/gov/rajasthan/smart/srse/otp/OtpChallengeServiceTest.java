package gov.rajasthan.smart.srse.otp;

import gov.rajasthan.smart.srse.identity.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpChallengeServiceTest {

    @Mock
    private UserOtpChallengeRepository challengeRepository;
    @Mock
    private OtpCodeGenerator codeGenerator;
    @Mock
    private OtpDeliveryService deliveryService;

    private OtpChallengeService service;

    @BeforeEach
    void setUp() {
        service = new OtpChallengeService(
                challengeRepository,
                codeGenerator,
                deliveryService,
                new OtpProperties(OtpProperties.Sender.LOG, 5, 3, 60));
    }

    @Test
    void loginIssuesOneStoredChallengeAndDeliversOnce() {
        AppUser user = mfaUser();
        when(codeGenerator.generateSixDigitCode()).thenReturn("123456");
        when(deliveryService.deliverLoginCode(user, "123456"))
                .thenReturn(new OtpDeliveryService.DeliveryOutcome(
                        java.util.List.of(
                                new OtpSendResult(OtpChannel.EMAIL, true, "ok"),
                                new OtpSendResult(OtpChannel.SMS, true, "ok")),
                        true));
        when(challengeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.beginLoginChallenge(user);

        verify(challengeRepository).invalidateActiveForUser(anyLong(), eq(OtpPurpose.LOGIN), any());
        ArgumentCaptor<UserOtpChallenge> saved = ArgumentCaptor.forClass(UserOtpChallenge.class);
        verify(challengeRepository).save(saved.capture());
        assertEquals(OtpPurpose.LOGIN, saved.getValue().getPurpose());
        verify(codeGenerator, times(1)).generateSixDigitCode();
        verify(deliveryService, times(1)).deliverLoginCode(eq(user), eq("123456"));
    }

    @Test
    void allChannelsFailRefusesLoginChallenge() {
        AppUser user = mfaUser();
        when(codeGenerator.generateSixDigitCode()).thenReturn("123456");
        when(deliveryService.deliverLoginCode(user, "123456"))
                .thenReturn(new OtpDeliveryService.DeliveryOutcome(
                        java.util.List.of(new OtpSendResult(OtpChannel.EMAIL, false, "fail")),
                        false));

        assertThrows(OtpDeliveryException.class, () -> service.beginLoginChallenge(user));
    }

    @Test
    void wrongPurposeRejectedOnVerify() {
        AppUser user = mfaUser();
        UserOtpChallenge row = activeChallenge(user, OtpPurpose.VERIFY_EMAIL, "123456");
        when(challengeRepository.findByPublicChallengeId("cid")).thenReturn(Optional.of(row));

        assertThrows(IllegalArgumentException.class,
                () -> service.verifyAndConsume("cid", "123456", OtpPurpose.LOGIN));
    }

    @Test
    void reuseRejectedAfterConsume() {
        AppUser user = mfaUser();
        UserOtpChallenge row = activeChallenge(user, OtpPurpose.LOGIN, "123456");
        row.setConsumedAt(Instant.now());
        when(challengeRepository.findByPublicChallengeId("cid")).thenReturn(Optional.of(row));

        assertThrows(IllegalArgumentException.class,
                () -> service.verifyAndConsume("cid", "123456", OtpPurpose.LOGIN));
    }

    private static AppUser mfaUser() {
        AppUser user = new AppUser("officer", "hash");
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 42L);
        user.setEmail("officer@example.gov");
        user.setMobile("9876543210");
        user.setEmailVerified(true);
        user.setMobileVerified(true);
        user.setMfaRequired(true);
        return user;
    }

    private static UserOtpChallenge activeChallenge(AppUser user, OtpPurpose purpose, String code) {
        Instant now = Instant.now();
        UserOtpChallenge row = new UserOtpChallenge(
                "cid",
                user,
                OtpHasher.hash(user.getId(), purpose, code),
                purpose,
                now,
                now.plus(5, ChronoUnit.MINUTES),
                "EMAIL:OK",
                now);
        return row;
    }
}
