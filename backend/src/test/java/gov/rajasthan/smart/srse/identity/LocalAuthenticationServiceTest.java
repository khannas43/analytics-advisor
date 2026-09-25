package gov.rajasthan.smart.srse.identity;

import gov.rajasthan.smart.srse.security.SessionTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalAuthenticationServiceTest {

    @Mock
    private AppUserRepository userRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private SessionTokenService sessionTokenService;

    private BCryptPasswordEncoder passwordEncoder;
    private LocalAuthenticationService service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(12);
        IdentityProperties props = new IdentityProperties(
                new IdentityProperties.Lockout(3, 10),
                new IdentityProperties.Password(90),
                new IdentityProperties.Bootstrap("superadmin"));
        service = new LocalAuthenticationService(
                userRepository, userRoleRepository, passwordEncoder, sessionTokenService, props);
    }

    @Test
    void unknownUserAndWrongPasswordReturnSameMessage() {
        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.empty());
        AppUser bob = activeUser("bob", passwordEncoder.encode("correct"));
        when(userRepository.findByUsernameIgnoreCase("bob")).thenReturn(Optional.of(bob));

        var unknown = service.login("alice", "wrong");
        var wrong = service.login("bob", "wrong");

        assertFalse(unknown.success());
        assertFalse(wrong.success());
        assertEquals(unknown.message(), wrong.message());
        assertEquals(LocalAuthenticationService.INVALID_CREDENTIALS_MESSAGE, unknown.message());
    }

    @Test
    void successfulLoginIssuesToken() {
        AppUser user = activeUser("officer1", passwordEncoder.encode("secret"));
        when(userRepository.findByUsernameIgnoreCase("officer1")).thenReturn(Optional.of(user));
        when(userRoleRepository.findRoleCodesByUserId(1L)).thenReturn(List.of(AppRole.OFFICER));
        when(sessionTokenService.issue(anyString(), any(), anyLong(), any(Boolean.class))).thenReturn("jwt");

        var result = service.login("officer1", "secret");

        assertTrue(result.success());
        assertEquals("jwt", result.token());
    }

    @Test
    void deactivatedUserCannotLogin() {
        AppUser user = activeUser("officer1", passwordEncoder.encode("secret"));
        user.setActive(false);
        when(userRepository.findByUsernameIgnoreCase("officer1")).thenReturn(Optional.of(user));

        var result = service.login("officer1", "secret");

        assertFalse(result.success());
        assertEquals(LocalAuthenticationService.INVALID_CREDENTIALS_MESSAGE, result.message());
    }

    @Test
    void lockoutAfterRepeatedFailures() {
        AppUser user = activeUser("officer1", passwordEncoder.encode("secret"));
        when(userRepository.findByUsernameIgnoreCase("officer1")).thenReturn(Optional.of(user));

        service.login("officer1", "bad");
        service.login("officer1", "bad");
        service.login("officer1", "bad");

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository, org.mockito.Mockito.atLeast(3)).save(captor.capture());
        assertNotNull(captor.getValue().getLockedUntil());
    }

    @Test
    void changePasswordIncrementsSessionVersion() {
        AppUser user = activeUser("officer1", passwordEncoder.encode("old"));
        when(userRepository.findByUsernameIgnoreCase("officer1")).thenReturn(Optional.of(user));

        service.changePassword("officer1", "old", "new-password-12");

        assertEquals(1L, user.getSessionVersion());
        assertFalse(user.isMustChangePassword());
    }

    @Test
    void deactivateIncrementsSessionVersion() {
        AppUser user = activeUser("officer1", passwordEncoder.encode("x"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.deactivate(1L);

        assertFalse(user.isActive());
        assertEquals(1L, user.getSessionVersion());
    }

    private static AppUser activeUser(String username, String hash) {
        AppUser user = new AppUser(username, hash);
        user.setPasswordChangedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}
