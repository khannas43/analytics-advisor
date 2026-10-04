package gov.rajasthan.smart.srse.identity;

import gov.rajasthan.smart.srse.audit.AuditActionType;
import gov.rajasthan.smart.srse.audit.AuditOutcome;
import gov.rajasthan.smart.srse.audit.AuditService;
import gov.rajasthan.smart.srse.otp.ContactMasking;
import gov.rajasthan.smart.srse.otp.OtpChallengeService;
import gov.rajasthan.smart.srse.otp.OtpDeliveryException;
import gov.rajasthan.smart.srse.otp.OtpPurpose;
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
    private final OtpChallengeService otpChallengeService;
    private final AuditService auditService;

    public LocalAuthenticationService(
            AppUserRepository userRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder,
            SessionTokenService sessionTokenService,
            IdentityProperties properties,
            OtpChallengeService otpChallengeService,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionTokenService = sessionTokenService;
        this.properties = properties;
        this.otpChallengeService = otpChallengeService;
        this.auditService = auditService;
    }

    @Transactional
    public LoginResult login(String username, String rawPassword) {
        Optional<AppUser> userOpt = userRepository.findByUsernameIgnoreCase(username);
        if (userOpt.isEmpty()) {
            passwordEncoder.matches(rawPassword, DUMMY_PASSWORD_HASH);
            auditService.recordAuthEvent(
                    AuditActionType.LOGIN_FAILED, AuditOutcome.FAILURE, null, null, "login");
            return LoginResult.failure(INVALID_CREDENTIALS_MESSAGE);
        }
        AppUser user = userOpt.get();
        if (!user.isActive() || isLocked(user)) {
            passwordEncoder.matches(rawPassword, DUMMY_PASSWORD_HASH);
            auditService.recordAuthEvent(
                    AuditActionType.LOGIN_FAILED, AuditOutcome.FAILURE, user.getId(), user.getId(), "login");
            return LoginResult.failure(INVALID_CREDENTIALS_MESSAGE);
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            registerFailedAttempt(user);
            auditService.recordAuthEvent(
                    AuditActionType.LOGIN_FAILED, AuditOutcome.FAILURE, user.getId(), user.getId(), "login");
            return LoginResult.failure(INVALID_CREDENTIALS_MESSAGE);
        }
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.touchUpdatedAt();
        userRepository.save(user);

        if (user.isMfaRequired()) {
            if (!user.isEmailVerified() && !user.isMobileVerified()) {
                return LoginResult.failure(
                        "MFA is enabled but you have no verified contact — contact an administrator.");
            }
            try {
                OtpChallengeService.IssuedChallenge challenge = otpChallengeService.beginLoginChallenge(user);
                auditService.recordAuthEvent(
                        AuditActionType.MFA_CHALLENGE_ISSUED,
                        AuditOutcome.SUCCESS,
                        user.getId(),
                        user.getId(),
                        "login");
                return LoginResult.mfaPending(
                        challenge.publicChallengeId(),
                        ContactMasking.maskEmail(user.getEmail()),
                        ContactMasking.maskMobile(user.getMobile()));
            } catch (OtpDeliveryException ex) {
                return LoginResult.failure(ex.getMessage());
            }
        }
        LoginResult result = completeSessionLogin(user);
        auditService.recordAuthEvent(
                AuditActionType.LOGIN_SUCCESS, AuditOutcome.SUCCESS, user.getId(), user.getId(), "login");
        return result;
    }

    @Transactional
    public LoginResult verifyLoginOtp(String challengeId, String code) {
        try {
            AppUser user = otpChallengeService.verifyAndConsume(challengeId, code, OtpPurpose.LOGIN);
            auditService.recordAuthEvent(
                    AuditActionType.MFA_VERIFIED, AuditOutcome.SUCCESS, user.getId(), user.getId(), "login");
            user.setLastLoginAt(Instant.now());
            user.touchUpdatedAt();
            userRepository.save(user);
            LoginResult result = completeSessionLogin(user);
            auditService.recordAuthEvent(
                    AuditActionType.LOGIN_SUCCESS, AuditOutcome.SUCCESS, user.getId(), user.getId(), "login");
            return result;
        } catch (IllegalArgumentException ex) {
            auditService.recordAuthEvent(
                    AuditActionType.MFA_FAILED, AuditOutcome.FAILURE, null, null, "login");
            throw ex;
        }
    }

    @Transactional
    public LoginResult resendLoginOtp(String challengeId) {
        try {
            OtpChallengeService.IssuedChallenge challenge = otpChallengeService.resend(challengeId);
            AppUser user = challenge.user();
            return LoginResult.mfaPending(
                    challenge.publicChallengeId(),
                    ContactMasking.maskEmail(user.getEmail()),
                    ContactMasking.maskMobile(user.getMobile()));
        } catch (OtpDeliveryException ex) {
            return LoginResult.failure(ex.getMessage());
        }
    }

    private LoginResult completeSessionLogin(AppUser user) {
        boolean mustChange = user.isMustChangePassword() || isPasswordExpired(user);
        List<String> authorities = RoleAuthorityMapper.toAuthorities(
                userRoleRepository.findRoleCodesByUserId(user.getId()));
        String token = sessionTokenService.issue(
                user.getUsername(), authorities, user.getSessionVersion(), mustChange);
        return LoginResult.success(token, mustChange);
    }

    /**
     * Re-issues a session token carrying only the authorities for one assigned role.
     * Lets a multi-role user work as an officer without admin surfaces, or vice versa.
     */
    @Transactional(readOnly = true)
    public LoginResult switchActiveRole(String username, String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new IllegalArgumentException("role is required");
        }
        AppUser user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException(INVALID_CREDENTIALS_MESSAGE));
        if (!user.isActive()) {
            throw new IllegalArgumentException(INVALID_CREDENTIALS_MESSAGE);
        }
        List<String> assigned = userRoleRepository.findRoleCodesByUserId(user.getId());
        String normalized = roleCode.trim().toUpperCase();
        if (!assigned.contains(normalized)) {
            throw new IllegalArgumentException("That role is not assigned to this user");
        }
        boolean mustChange = user.isMustChangePassword() || isPasswordExpired(user);
        List<String> authorities = RoleAuthorityMapper.toAuthorities(List.of(normalized));
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
            auditService.recordAuthEvent(
                    AuditActionType.ACCOUNT_LOCKED,
                    AuditOutcome.SUCCESS,
                    user.getId(),
                    user.getId(),
                    "login");
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

    public record LoginResult(
            boolean success,
            String token,
            String message,
            boolean mustChangePassword,
            String mfaChallengeId,
            String maskedEmail,
            String maskedMobile) {

        static LoginResult success(String token, boolean mustChangePassword) {
            return new LoginResult(true, token, null, mustChangePassword, null, null, null);
        }

        static LoginResult mfaPending(String challengeId, String maskedEmail, String maskedMobile) {
            return new LoginResult(true, null, null, false, challengeId, maskedEmail, maskedMobile);
        }

        static LoginResult failure(String message) {
            return new LoginResult(false, null, message, false, null, null, null);
        }

        public boolean mfaRequired() {
            return success && mfaChallengeId != null;
        }
    }
}
