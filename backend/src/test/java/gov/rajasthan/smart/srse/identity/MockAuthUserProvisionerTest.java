package gov.rajasthan.smart.srse.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MockAuthUserProvisionerTest {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private AppRoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private MockAuthUserProvisioner provisioner;

    @BeforeEach
    void setUp() {
        provisioner = new MockAuthUserProvisioner(
                userRepository, roleRepository, userRoleRepository, passwordEncoder);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(roleRepository.findByCode(AppRole.ADMIN))
                .thenReturn(Optional.of(new AppRole(AppRole.ADMIN, "Admin")));
        when(roleRepository.findByCode(AppRole.OFFICER))
                .thenReturn(Optional.of(new AppRole(AppRole.OFFICER, "Officer")));
    }

    @Test
    void createsMockAdminAndOfficerWhenMissing() {
        when(userRepository.findByUsernameIgnoreCase(MockAuthUserProvisioner.MOCK_ADMIN_USERNAME))
                .thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase(MockAuthUserProvisioner.MOCK_OFFICER_USERNAME))
                .thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            u.getClass().getDeclaredFields();
            return u;
        });
        when(userRoleRepository.findRoleCodesByUserId(any())).thenReturn(List.of());

        provisioner.run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(AppUser::getUsername)
                .containsExactlyInAnyOrder(
                        MockAuthUserProvisioner.MOCK_ADMIN_USERNAME,
                        MockAuthUserProvisioner.MOCK_OFFICER_USERNAME);
        verify(userRoleRepository, org.mockito.Mockito.times(2)).save(any(UserRole.class));
    }

    @Test
    void idempotentWhenUsersAlreadyExist() {
        AppUser admin = new AppUser(MockAuthUserProvisioner.MOCK_ADMIN_USERNAME, "hash");
        AppUser officer = new AppUser(MockAuthUserProvisioner.MOCK_OFFICER_USERNAME, "hash");
        when(userRepository.findByUsernameIgnoreCase(MockAuthUserProvisioner.MOCK_ADMIN_USERNAME))
                .thenReturn(Optional.of(admin));
        when(userRepository.findByUsernameIgnoreCase(MockAuthUserProvisioner.MOCK_OFFICER_USERNAME))
                .thenReturn(Optional.of(officer));
        when(userRoleRepository.findRoleCodesByUserId(any()))
                .thenReturn(List.of(AppRole.ADMIN), List.of(AppRole.OFFICER));

        provisioner.run(new DefaultApplicationArguments(new String[0]));

        verify(userRepository, never()).save(any());
    }
}
