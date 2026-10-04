package gov.rajasthan.smart.srse.identity;

import gov.rajasthan.smart.srse.otp.OtpPurpose;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = "srse.auth-mode", havingValue = "local")
public class LocalAuthController {

    private final LocalAuthenticationService authenticationService;
    private final ContactVerificationService contactVerificationService;
    private final AppUserRepository userRepository;

    public LocalAuthController(
            LocalAuthenticationService authenticationService,
            ContactVerificationService contactVerificationService,
            AppUserRepository userRepository) {
        this.authenticationService = authenticationService;
        this.contactVerificationService = contactVerificationService;
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LocalAuthenticationService.LoginResult result =
                authenticationService.login(request.username(), request.password());
        if (!result.success()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(null, result.message(), false, null, null, null));
        }
        if (result.mfaRequired()) {
            return ResponseEntity.ok(new LoginResponse(
                    null,
                    null,
                    false,
                    result.mfaChallengeId(),
                    result.maskedEmail(),
                    result.maskedMobile()));
        }
        return ResponseEntity.ok(new LoginResponse(
                result.token(), null, result.mustChangePassword(), null, null, null));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<LoginResponse> verifyOtp(@RequestBody VerifyOtpRequest request) {
        try {
            LocalAuthenticationService.LoginResult result = authenticationService.verifyLoginOtp(
                    request.challengeId(), request.code());
            return ResponseEntity.ok(new LoginResponse(
                    result.token(), null, result.mustChangePassword(), null, null, null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(null, ex.getMessage(), false, null, null, null));
        }
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<LoginResponse> resendOtp(@RequestBody ResendOtpRequest request) {
        LocalAuthenticationService.LoginResult result = authenticationService.resendLoginOtp(request.challengeId());
        if (!result.success()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new LoginResponse(null, result.message(), false, null, null, null));
        }
        return ResponseEntity.ok(new LoginResponse(
                null,
                null,
                false,
                result.mfaChallengeId(),
                result.maskedEmail(),
                result.maskedMobile()));
    }

    @PostMapping("/contact/send-verification")
    public ResponseEntity<ContactChallengeResponse> sendContactVerification(
            @RequestBody ContactVerificationRequest request,
            org.springframework.security.core.Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AppUser user = userRepository.findByUsernameIgnoreCase(authentication.getName())
                .orElseThrow();
        OtpPurpose purpose = "MOBILE".equalsIgnoreCase(request.channel())
                ? OtpPurpose.VERIFY_MOBILE
                : OtpPurpose.VERIFY_EMAIL;
        String challengeId = contactVerificationService.sendVerification(user, purpose);
        return ResponseEntity.ok(new ContactChallengeResponse(challengeId));
    }

    @PostMapping("/contact/verify")
    public ResponseEntity<MessageResponse> verifyContact(
            @RequestBody ContactVerifyRequest request,
            org.springframework.security.core.Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AppUser user = userRepository.findByUsernameIgnoreCase(authentication.getName())
                .orElseThrow();
        OtpPurpose purpose = "MOBILE".equalsIgnoreCase(request.channel())
                ? OtpPurpose.VERIFY_MOBILE
                : OtpPurpose.VERIFY_EMAIL;
        contactVerificationService.confirmVerification(user, request.challengeId(), request.code(), purpose);
        return ResponseEntity.ok(new MessageResponse("Contact verified."));
    }

    @PostMapping("/switch-role")
    public ResponseEntity<LoginResponse> switchRole(
            @RequestBody SwitchRoleRequest request,
            org.springframework.security.core.Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            LocalAuthenticationService.LoginResult result = authenticationService.switchActiveRole(
                    authentication.getName(), request.role());
            return ResponseEntity.ok(new LoginResponse(
                    result.token(), null, result.mustChangePassword(), null, null, null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new LoginResponse(null, ex.getMessage(), false, null, null, null));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<MessageResponse> changePassword(
            @RequestBody ChangePasswordRequest request,
            org.springframework.security.core.Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            authenticationService.changePassword(
                    authentication.getName(), request.currentPassword(), request.newPassword());
            return ResponseEntity.ok(new MessageResponse("Password updated."));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new MessageResponse(ex.getMessage()));
        }
    }

    public record SwitchRoleRequest(String role) {
    }

    public record LoginRequest(String username, String password) {
    }

    public record LoginResponse(
            String token,
            String message,
            boolean mustChangePassword,
            String mfaChallengeId,
            String maskedEmail,
            String maskedMobile) {
    }

    public record VerifyOtpRequest(String challengeId, String code) {
    }

    public record ResendOtpRequest(String challengeId) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {
    }

    public record MessageResponse(String message) {
    }

    public record ContactVerificationRequest(String channel) {
    }

    public record ContactChallengeResponse(String challengeId) {
    }

    public record ContactVerifyRequest(String channel, String challengeId, String code) {
    }
}
