package gov.rajasthan.smart.srse.identity;

import java.util.List;
import java.util.Map;

import gov.rajasthan.smart.srse.scope.UserScopeAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Regression test for a privilege escalation found by driving the live API.
 *
 * <p>Rule 2 asks whether every assignment the target holds sits inside the
 * caller's subtree. A target holding <em>no</em> assignments satisfies that
 * vacuously — the loop never runs — and the bootstrap SuperAdmin holds none. A
 * scoped district admin was therefore treated as able to manage the SuperAdmin.
 * Role changes and deactivation were blocked by their own rules, but
 * {@code reset-password} had no such guard, so the district admin set the
 * SuperAdmin's password and logged in as them. Confirmed end to end against a
 * running backend before this test existed.
 *
 * <p>Two independent guards now stop it, and both are asserted here. Either
 * would do; the pair survives one of them being reasoned away later.
 */
@ExtendWith(MockitoExtension.class)
class AdminCannotReachSuperAdminTest {

    private static final long GEO = 1L;

    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private UserScopeAssignmentService assignmentLoader;

    private AdminAuthorizationService authorization;
    private AppUser scopedAdmin;
    private AppUser superAdmin;
    private AppUser officer;

    @BeforeEach
    void setUp() {
        authorization = new AdminAuthorizationService(userRoleRepository, assignmentLoader);
        superAdmin = user(1L, "super");
        scopedAdmin = user(2L, "jaipur-admin");
        officer = user(3L, "officer");
    }

    @Test
    void aScopedAdminCannotManageAUserHoldingNoAssignments() {
        stubRoles(scopedAdmin, AppRole.ADMIN);
        // OFFICER, not SUPER_ADMIN — isolates the no-assignment guard from the
        // role guard, so this test fails if only the role guard is present.
        stubRoles(officer, AppRole.OFFICER);
        stubAssignments(scopedAdmin, Map.of(GEO, List.of("/G/JAIPUR/")));
        stubAssignments(officer, Map.of());

        assertFalse(authorization.canManageUser(scopedAdmin, officer),
                "a user in no subtree belongs to no scoped admin — vacuous truth is not permission");
    }

    @Test
    void aScopedAdminCannotManageASuperAdminWhateverTheScopesSay() {
        stubRoles(scopedAdmin, AppRole.ADMIN);
        stubRoles(superAdmin, AppRole.SUPER_ADMIN);
        // No assignments are stubbed for either side, and that is the assertion
        // as much as the return value is: Mockito's strict stubbing fails this
        // test if the scope loader is consulted at all. The role guard must
        // decide before scopes are read, so it cannot evaporate the day someone
        // gives the SuperAdmin an assignment inside an admin's subtree.
        assertFalse(authorization.canManageUser(scopedAdmin, superAdmin),
                "holding SUPER_ADMIN puts a user out of reach regardless of where they sit");
    }

    @Test
    void anOrdinaryUserInsideTheSubtreeIsStillManageable() {
        stubRoles(scopedAdmin, AppRole.ADMIN);
        stubRoles(officer, AppRole.OFFICER);
        stubAssignments(scopedAdmin, Map.of(GEO, List.of("/G/JAIPUR/")));
        stubAssignments(officer, Map.of(GEO, List.of("/G/JAIPUR/SANGANER/")));

        assertTrue(authorization.canManageUser(scopedAdmin, officer),
                "the fix must not lock admins out of the users they legitimately own");
    }

    private void stubRoles(AppUser user, String... roles) {
        when(userRoleRepository.findRoleCodesByUserId(user.getId())).thenReturn(List.of(roles));
    }

    private void stubAssignments(AppUser user, Map<Long, List<String>> paths) {
        when(assignmentLoader.assignmentPathsByDimension(user.getId())).thenReturn(paths);
    }

    private static AppUser user(long id, String name) {
        AppUser u = new AppUser(name, "hash");
        try {
            var field = AppUser.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(u, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return u;
    }
}
