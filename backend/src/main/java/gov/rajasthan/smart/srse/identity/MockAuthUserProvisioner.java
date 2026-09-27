package gov.rajasthan.smart.srse.identity;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists deterministic {@code app_user} rows for mock JWT principals so audited
 * admin mutations ({@link gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService#register},
 * scope binding writes, etc.) resolve a real actor in mock mode.
 *
 * <p>Mock login still issues tokens without a password check; these accounts exist
 * only so {@link AuthenticatedUserService#requireCurrentUser()} and audit attribution
 * work on the default docker-compose stack.
 */
@Component
@ConditionalOnProperty(name = "srse.auth-mode", havingValue = "mock", matchIfMissing = true)
@Order(10)
public class MockAuthUserProvisioner implements ApplicationRunner {

    public static final String MOCK_ADMIN_USERNAME = "mock-admin";
    public static final String MOCK_OFFICER_USERNAME = "mock-officer";

    private final AppUserRepository userRepository;
    private final AppRoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public MockAuthUserProvisioner(
            AppUserRepository userRepository,
            AppRoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureUser(MOCK_OFFICER_USERNAME, AppRole.OFFICER);
        ensureUser(MOCK_ADMIN_USERNAME, AppRole.ADMIN);
    }

    private void ensureUser(String username, String roleCode) {
        AppUser user = userRepository.findByUsernameIgnoreCase(username).orElse(null);
        if (user == null) {
            user = new AppUser(username, passwordEncoder.encode("mock-dev-credential-unused"));
            user.setMfaRequired(false);
            user.setMustChangePassword(false);
            user.setPasswordChangedAt(java.time.Instant.now());
            user = userRepository.save(user);
        }
        AppRole role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("Missing role: " + roleCode));
        boolean hasRole = userRoleRepository.findRoleCodesByUserId(user.getId()).contains(roleCode);
        if (!hasRole) {
            userRoleRepository.save(new UserRole(user, role));
        }
    }
}
