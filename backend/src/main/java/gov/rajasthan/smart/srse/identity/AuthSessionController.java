package gov.rajasthan.smart.srse.identity;

import gov.rajasthan.smart.srse.security.Authorities;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import gov.rajasthan.smart.srse.audit.AuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthSessionController {

    private final AppUserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditService auditService;

    public AuthSessionController(
            AppUserRepository userRepository,
            UserRoleRepository userRoleRepository,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.auditService = auditService;
    }

    /** Client discards the JWT; server records the logout for audit (§7.3). */
    @PostMapping("/logout")
    public void logout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new AdminAccessDeniedException("Not authenticated");
        }
        userRepository.findByUsernameIgnoreCase(authentication.getName())
                .ifPresent(auditService::recordLogout);
    }

    @GetMapping("/session")
    public SessionView currentSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new AdminAccessDeniedException("Not authenticated");
        }
        List<String> authorities = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .toList();
        return userRepository.findByUsernameIgnoreCase(authentication.getName())
                .map(user -> {
                    List<String> roles = userRoleRepository.findRoleCodesByUserId(user.getId());
                    return new SessionView(
                            user.getUsername(),
                            roles,
                            authorities,
                            authorities.contains(Authorities.SRSE_ADMIN),
                            roles.contains(AppRole.SUPER_ADMIN),
                            user.isMustChangePassword(),
                            user.isActive());
                })
                .orElseGet(() -> new SessionView(
                        authentication.getName(),
                        List.of(),
                        authorities,
                        authorities.contains(Authorities.SRSE_ADMIN),
                        false,
                        false,
                        true));
    }

    public record SessionView(
            String username,
            List<String> roles,
            List<String> authorities,
            boolean admin,
            boolean superAdmin,
            boolean mustChangePassword,
            boolean active) {
    }
}
