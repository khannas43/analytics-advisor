package gov.rajasthan.smart.srse.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first SuperAdmin when {@code app_user} is empty (7.1b). Never runs again.
 */
@Component
@Order(0)
public class SuperAdminBootstrapRunner implements ApplicationRunner {

    private final AppUserRepository userRepository;
    private final AppRoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final IdentityProperties properties;

    @Value("${srse.bootstrap.super-admin-password:}")
    private String bootstrapPassword;

    public SuperAdminBootstrapRunner(
            AppUserRepository userRepository,
            AppRoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
            IdentityProperties properties) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        if (bootstrapPassword == null || bootstrapPassword.isBlank()) {
            throw new BootstrapPasswordRequiredException(
                    "app_user is empty but no bootstrap password was configured "
                            + "(set SRSE_BOOTSTRAP_SUPER_ADMIN_PASSWORD)");
        }
        AppRole superAdminRole = roleRepository.findByCode(AppRole.SUPER_ADMIN)
                .orElseThrow(() -> new IllegalStateException("SUPER_ADMIN role missing — run Liquibase"));
        AppUser user = new AppUser(properties.bootstrap().superAdminUsername(),
                passwordEncoder.encode(bootstrapPassword));
        user.setMustChangePassword(true);
        user.setMfaRequired(false);
        user.setPasswordChangedAt(java.time.Instant.now());
        user = userRepository.save(user);
        userRoleRepository.save(new UserRole(user, superAdminRole));
    }
}
