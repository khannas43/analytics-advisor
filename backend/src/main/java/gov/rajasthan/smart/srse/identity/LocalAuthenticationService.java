package gov.rajasthan.smart.srse.identity;

import gov.rajasthan.smart.srse.security.SessionTokenService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "srse.auth-mode", havingValue = "local")
public class LocalAuthenticationService {

    /** Same response for unknown user and bad password — do not distinguish. */
    public static final String INVALID_CREDENTIALS_MESSAGE = "Invalid username or password";

    /**
     * BCrypt hash of a dummy password — matched on the unknown-user path so timing
     * does not reveal whether the username exists.
     */
    static final String DUMMY_PASSWORD_HASH =
            "$2a$12$dYQ0KCu7DJMx/koOjW.mwOhkbLdmzzVu7dNrwphrkslf/Rp09GKyi";

    private final AppUserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionTokenService sessionTokenService;
    private final IdentityProperties properties;

    public LocalAuthenticationService(
            AppUserRepository userRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder,
            SessionTokenService sessionTokenService,
            IdentityProperties properties) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionTokenService = sessionTokenService;
        this.properties = properties;
    }

    @Transactional
    public LoginResult login(String username, String rawPassword) {
        Optional<AppUser> userOpt = userRepository.findByUsernameIgnoreCase(username);
        if (userOpt.isEmpty()) {
            passwordEncoder.matches(rawPassword, DUMMY_PASSWORD_HASH);
            return LoginResult.failure(INVALID_CREDENTIALS_MESSAGE);
        }
        AppUser user = userOpt.get();
        if (!user.isActive() || isLocked(user)) {
            passwordEncoder.matches(rawPassword, DUMMY_PASSWORD_HASH);
            return LoginResult.failure(INVALID_CREDENTIALS_MESSAGE);
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            registerFailedAttempt(user);
            return LoginResult.failure(INVALID_CREDENTIALS_MESSAGE);
        }
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        user.touchUpdatedAt();
        userRepository.save(user);

        boolean mustChange = user.isMustChangePassword() || isPasswordExpired(user);
        List<String> authorities = RoleAuthorityMapper.toAuthorities(
                userRoleRepository.findRoleCodesByUserId(user.getId()));
        String token = sessionTokenService.issue(
                user.getUsername(), authorities, user.getSessionVersion(), mustChange);
        return LoginResult.success(token, mustChange);
    }

    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        AppUser user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException(INVALID_CREDENTIALS_MESSAGE));
        if (!user.isActive()) {
            throw new IllegalArgumentException(INVALID_CREDENTIALS_MESSAGE);
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException(INVALID_CREDENTIALS_MESSAGE);
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(Instant.now());
        user.incrementSessionVersion();
        user.touchUpdatedAt();
        userRepository.save(user);
    }

    @Transactional
    public void deactivate(long userId) {
        AppUser user = userRepository.findById(userId).orElseThrow();
        user.setActive(false);
        user.incrementSessionVersion();
        user.touchUpdatedAt();
        userRepository.save(user);
    }

    private boolean isLocked(AppUser user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now());
    }

    private void registerFailedAttempt(AppUser user) {
        int attempts = user.getFailedLoginCount() + 1;
        user.setFailedLoginCount(attempts);
        if (attempts >= properties.lockout().maxFailedAttempts()) {
            user.setLockedUntil(Instant.now().plus(
                    properties.lockout().lockDurationMinutes(), ChronoUnit.MINUTES));
            user.setFailedLoginCount(0);
        }
        user.touchUpdatedAt();
        userRepository.save(user);
    }

    private boolean isPasswordExpired(AppUser user) {
        if (user.getPasswordChangedAt() == null) {
            return false;
        }
        Instant expires = user.getPasswordChangedAt()
                .plus(properties.password().expiryDays(), ChronoUnit.DAYS);
        return Instant.now().isAfter(expires);
    }

    public record LoginResult(boolean success, String token, String message, boolean mustChangePassword) {
        static LoginResult success(String token, boolean mustChangePassword) {
            return new LoginResult(true, token, null, mustChangePassword);
        }

        static LoginResult failure(String message) {
            return new LoginResult(false, null, message, false);
        }
    }
}
