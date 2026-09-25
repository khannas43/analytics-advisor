package gov.rajasthan.smart.srse.identity;

import gov.rajasthan.smart.srse.otp.OtpChallengeService;
import gov.rajasthan.smart.srse.otp.OtpDeliveryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MfaLoginFlowTest {

    @Mock
    private AppUserRepository userRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private OtpChallengeService otpChallengeService;
    @Mock
    private gov.rajasthan.smart.srse.security.SessionTokenService sessionTokenService;

    private LocalAuthenticationService service;
    private BCryptPasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new BCryptPasswordEncoder(12);
        IdentityProperties props = new IdentityProperties(
                new IdentityProperties.Lockout(5, 10),
                new IdentityProperties.Password(90),
                new IdentityProperties.Bootstrap("superadmin"));
        service = new LocalAuthenticationService(
                userRepository,
                userRoleRepository,
                encoder,
                sessionTokenService,
                props,
                otpChallengeService);
    }

    @Test
    void nonMfaLoginUnchanged() {
        AppUser user = user("plain", "secret", false);
        when(userRepository.findByUsernameIgnoreCase("plain")).thenReturn(Optional.of(user));
        when(userRoleRepository.findRoleCodesByUserId(1L)).thenReturn(List.of(AppRole.OFFICER));
        when(sessionTokenService.issue(anyString(), any(), anyLong(), anyBoolean())).thenReturn("session-jwt");

        var result = service.login("plain", "secret");

        assertEquals("session-jwt", result.token());
        assertNull(result.mfaChallengeId());
        verify(sessionTokenService).issue(anyString(), any(), anyLong(), anyBoolean());
    }

    @Test
    void mfaUserGetsChallengeNotSession() {
        AppUser user = user("mfa", "secret", true);
        user.setEmailVerified(true);
        user.setEmail("a@b.gov");
        when(userRepository.findByUsernameIgnoreCase("mfa")).thenReturn(Optional.of(user));
        when(otpChallengeService.beginLoginChallenge(user))
                .thenReturn(new OtpChallengeService.IssuedChallenge(
                        "challenge-uuid",
                        gov.rajasthan.smart.srse.otp.OtpPurpose.LOGIN,
                        user));

        var result = service.login("mfa", "secret");

        assertTrue(result.mfaRequired());
        assertNull(result.token());
        assertEquals("challenge-uuid", result.mfaChallengeId());
        verify(sessionTokenService, never()).issue(anyString(), any(), anyLong(), anyBoolean());
    }

    @Test
    void mfaWithoutVerifiedContactBlocked() {
        AppUser user = user("mfa", "secret", true);
        user.setEmailVerified(false);
        user.setMobileVerified(false);
        when(userRepository.findByUsernameIgnoreCase("mfa")).thenReturn(Optional.of(user));

        var result = service.login("mfa", "secret");

        assertTrue(result.message().contains("verified contact"));
        verify(otpChallengeService, never()).beginLoginChallenge(any());
    }

    private static AppUser user(String name, String password, boolean mfa) {
        AppUser user = new AppUser(name, new BCryptPasswordEncoder(12).encode(password));
        user.setPasswordChangedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        user.setMfaRequired(mfa);
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}
