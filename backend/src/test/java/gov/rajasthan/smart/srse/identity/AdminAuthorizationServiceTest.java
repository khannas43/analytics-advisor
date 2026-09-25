package gov.rajasthan.smart.srse.identity;

import gov.rajasthan.smart.srse.scope.ScopeDimension;
import gov.rajasthan.smart.srse.scope.ScopeLevel;
import gov.rajasthan.smart.srse.scope.ScopeNode;
import gov.rajasthan.smart.srse.scope.UserScopeAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** Part 3 authorization rules — enforced server-side (7.1.5 / 7.1.6). */
@ExtendWith(MockitoExtension.class)
class AdminAuthorizationServiceTest {

    private static final long GEO = 1L;
    private static final long DEPT = 2L;

    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private UserScopeAssignmentService assignmentLoader;

    private AdminAuthorizationService authorization;

    private AppUser superAdmin;
    private AppUser scopedAdmin;
    private AppUser targetUser;

    @BeforeEach
    void setUp() {
        authorization = new AdminAuthorizationService(userRoleRepository, assignmentLoader);
        superAdmin = user(1L, "super");
        scopedAdmin = user(2L, "jaipur-admin");
        targetUser = user(3L, "target");
    }

    @Test
    void rule1_adminCannotGrantOutsideSubtree() {
        stubRoles(scopedAdmin, AppRole.ADMIN);
        stubAssignments(scopedAdmin, Map.of(GEO, List.of("/G/JAIPUR/")));
        ScopeNode alwar = node(GEO, "/G/ALWAR/", "ALWAR");
        assertThrows(AdminAccessDeniedException.class,
                () -> authorization.assertCanGrantScopeNodes(scopedAdmin, List.of(alwar)));
    }

    @Test
    void rule1_superAdminMayGrantAnywhere() {
        stubRoles(superAdmin, AppRole.SUPER_ADMIN);
        ScopeNode alwar = node(GEO, "/G/ALWAR/", "ALWAR");
        assertDoesNotThrow(() -> authorization.assertCanGrantScopeNodes(superAdmin, List.of(alwar)));
    }

    @Test
    void rule2_userMustBeFullyWithinAdminSubtree_twoDimensions() {
        stubRoles(scopedAdmin, AppRole.ADMIN);
        stubAssignments(scopedAdmin, Map.of(
                GEO, List.of("/G/JAIPUR/"),
                DEPT, List.of("/D/HEALTH/")));
        stubAssignments(targetUser, Map.of(
                GEO, List.of("/G/JAIPUR/SANGANER/"),
                DEPT, List.of("/D/HEALTH/")));
        assertTrue(authorization.canManageUser(scopedAdmin, targetUser));

        AppUser straddler = user(4L, "straddler");
        stubAssignments(straddler, Map.of(
                GEO, List.of("/G/JAIPUR/", "/G/ALWAR/"),
                DEPT, List.of("/D/HEALTH/")));
        assertFalse(authorization.canManageUser(scopedAdmin, straddler));
    }

    @Test
    void rule3_scopedAdminCannotGrantSuperAdmin() {
        stubRoles(scopedAdmin, AppRole.ADMIN);
        assertThrows(AdminAccessDeniedException.class,
                () -> authorization.assertCanGrantRolesOnCreate(scopedAdmin, List.of(AppRole.SUPER_ADMIN)));
    }

    @Test
    void rule3_superAdminMayGrantSuperAdmin() {
        stubRoles(superAdmin, AppRole.SUPER_ADMIN);
        assertDoesNotThrow(() ->
                authorization.assertCanGrantRolesOnCreate(superAdmin, List.of(AppRole.SUPER_ADMIN)));
    }

    @Test
    void rule4_cannotEditOwnRolesOrScopes() {
        assertThrows(AdminAccessDeniedException.class,
                () -> authorization.assertNotSelfRoleOrScopeEdit(scopedAdmin, scopedAdmin));
    }

    @Test
    void rule5_cannotDeactivateSelf() {
        assertThrows(AdminAccessDeniedException.class,
                () -> authorization.assertCanDeactivate(scopedAdmin, scopedAdmin));
    }

    @Test
    void rule5_lastActiveSuperAdminCannotBeDeactivated() {
        stubRoles(superAdmin, AppRole.SUPER_ADMIN);
        when(userRoleRepository.countActiveUsersWithRoleExcluding(AppRole.SUPER_ADMIN, superAdmin.getId()))
                .thenReturn(0L);
        assertThrows(IllegalStateException.class,
                () -> authorization.assertCanDeactivate(scopedAdmin, superAdmin));
    }

    @Test
    void rule6_mfaRequiresVerifiedContact() {
        AppUser user = user(5L, "u");
        user.setMfaRequired(true);
        assertThrows(IllegalArgumentException.class,
                () -> authorization.assertMfaDeliverable(user, true));
        user.setEmailVerified(true);
        assertDoesNotThrow(() -> authorization.assertMfaDeliverable(user, true));
    }

    @Test
    void rule7_emptyRolesIsValid_noSpecialBlock() {
        stubRoles(superAdmin, AppRole.SUPER_ADMIN);
        assertTrue(authorization.canManageUser(superAdmin, targetUser));
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
        u.setActive(true);
        return u;
    }

    private static ScopeNode node(long dimensionId, String path, String code) {
        ScopeDimension d = new ScopeDimension("D", "D", 1);
        try {
            var f = ScopeDimension.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(d, dimensionId);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        ScopeLevel level = new ScopeLevel(d, 1, "L");
        return new ScopeNode(d, level, null, code, code, path);
    }
}
