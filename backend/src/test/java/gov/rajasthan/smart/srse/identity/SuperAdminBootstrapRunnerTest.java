package gov.rajasthan.smart.srse.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminBootstrapRunnerTest {

    @Mock
    private AppUserRepository userRepository;
    @Mock
    private AppRoleRepository roleRepository;
    @Mock
    private UserRoleRepository userRoleRepository;

    private SuperAdminBootstrapRunner runner;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    @BeforeEach
    void setUp() {
        IdentityProperties props = new IdentityProperties(
                new IdentityProperties.Lockout(5, 15),
                new IdentityProperties.Password(90),
                new IdentityProperties.Bootstrap("superadmin"));
        runner = new SuperAdminBootstrapRunner(
                userRepository, roleRepository, userRoleRepository, encoder, props);
        ReflectionTestUtils.setField(runner, "bootstrapPassword", "BootstrapPw1!");
    }

    @Test
    void createsSuperAdminOnlyWhenTableEmpty() {
        when(userRepository.count()).thenReturn(0L, 1L);
        AppRole role = new AppRole(AppRole.SUPER_ADMIN, "Super administrator");
        ReflectionTestUtils.setField(role, "id", 1L);
        when(roleRepository.findByCode(AppRole.SUPER_ADMIN)).thenReturn(Optional.of(role));
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 10L);
            return u;
        });

        runner.run(new DefaultApplicationArguments(new String[0]));
        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(userRepository, org.mockito.Mockito.times(1)).save(any(AppUser.class));
        verify(userRoleRepository, org.mockito.Mockito.times(1)).save(any(UserRole.class));
    }

    @Test
    void refusesStartWhenEmptyAndNoBootstrapPassword() {
        when(userRepository.count()).thenReturn(0L);
        ReflectionTestUtils.setField(runner, "bootstrapPassword", "  ");

        BootstrapPasswordRequiredException ex = assertThrows(
                BootstrapPasswordRequiredException.class,
                () -> runner.run(new DefaultApplicationArguments(new String[0])));
        org.junit.jupiter.api.Assertions.assertTrue(
                ex.getMessage().contains("SRSE_BOOTSTRAP_SUPER_ADMIN_PASSWORD"));
        verify(userRepository, never()).save(any());
    }
}
