package gov.rajasthan.smart.srse.lakehouse;

import java.util.Optional;

import gov.rajasthan.smart.srse.identity.AdminAuthorizationService;
import gov.rajasthan.smart.srse.identity.AppUserRepository;
import gov.rajasthan.smart.srse.identity.UserScopeAssignmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * An identity the service cannot place must be denied, never bypassed.
 *
 * <p>Three fallbacks here previously returned {@code bypass()}: no
 * authentication, no matching user row, and any auth mode other than
 * {@code local}. Each handed unrestricted access to every registered table on a
 * condition that reads like a technicality.
 *
 * <p>The third is the one that mattered. Decision 0.5 has SSO arriving after
 * local accounts, so {@code auth-mode} is expected to change — and on the day it
 * did, row scoping would have switched itself off silently, with every test
 * still green. These tests exist to make that a failure rather than a surprise.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfficerScopeFailsClosedTest {

    @Mock
    private AppUserRepository userRepository;
    @Mock
    private UserScopeAssignmentRepository assignmentRepository;
    @Mock
    private AdminAuthorizationService adminAuthorization;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private OfficerRegistryScopeService serviceFor(String authMode) {
        return new OfficerRegistryScopeService(
                userRepository, assignmentRepository, adminAuthorization, authMode);
    }

    @Test
    void anUnknownAuthModeDeniesRatherThanBypassing() {
        authenticateAs("someone");
        // rajsewadwar is the SSO mode the plan expects to adopt; anything not
        // explicitly named must land on the deny side.
        for (String mode : new String[] {"rajsewadwar", "oidc", "", "LOCAL_BUT_TYPOED"}) {
            var view = serviceFor(mode).currentOfficerScope();
            assertFalse(view.bypassDataScoping(),
                    "auth-mode '" + mode + "' must not grant unrestricted table access");
        }
    }

    @Test
    void localModeWithNoAuthenticationDenies() {
        SecurityContextHolder.clearContext();
        assertFalse(serviceFor("local").currentOfficerScope().bypassDataScoping());
    }

    @Test
    void localModeWithAnUnresolvableUserDenies() {
        authenticateAs("ghost");
        when(userRepository.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());
        assertFalse(serviceFor("local").currentOfficerScope().bypassDataScoping(),
                "a token naming a user who is not there must not become unrestricted access");
    }

    @Test
    void mockModeStillBypassesBecauseItHasNoUserModel() {
        authenticateAs("officer");
        assertTrue(serviceFor("mock").currentOfficerScope().bypassDataScoping(),
                "local development depends on this; it is the one named exception");
    }

    @Test
    void aDeniedViewStillSeesSharedReferenceDataOnly() {
        var denied = TableScopePolicy.OfficerScopeView.denyScoped();
        assertFalse(denied.bypassDataScoping());
        assertTrue(denied.assignmentsByDimension().isEmpty(),
                "denial is the no-assignments case, which D4 already limits to shared reference");
    }

    private static void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", java.util.List.of()));
    }
}
