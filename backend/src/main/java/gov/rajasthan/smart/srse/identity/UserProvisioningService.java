package gov.rajasthan.smart.srse.identity;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Creates users with an admin-set initial password (7.1.4). Admin UI arrives in 7.1.5. */
@Service
public class UserProvisioningService {

    private final AppUserRepository userRepository;
    private final AppRoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserProvisioningService(
            AppUserRepository userRepository,
            AppRoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AppUser createUser(String username, String initialPassword, List<String> roleCodes) {
        AppUser user = new AppUser(username, passwordEncoder.encode(initialPassword));
        user.setMustChangePassword(true);
        user.setPasswordChangedAt(Instant.now());
        user = userRepository.save(user);
        for (String code : roleCodes) {
            AppRole role = roleRepository.findByCode(code)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + code));
            userRoleRepository.save(new UserRole(user, role));
        }
        return user;
    }
}
