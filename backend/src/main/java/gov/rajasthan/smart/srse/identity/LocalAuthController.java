package gov.rajasthan.smart.srse.identity;

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

    public LocalAuthController(LocalAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LocalAuthenticationService.LoginResult result =
                authenticationService.login(request.username(), request.password());
        if (!result.success()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(null, result.message(), false));
        }
        return ResponseEntity.ok(new LoginResponse(result.token(), null, result.mustChangePassword()));
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

    public record LoginRequest(String username, String password) {
    }

    public record LoginResponse(String token, String message, boolean mustChangePassword) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {
    }

    public record MessageResponse(String message) {
    }
}
