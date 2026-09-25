package gov.rajasthan.smart.srse.identity;

import gov.rajasthan.smart.srse.identity.admin.UserAdminDtos;
import gov.rajasthan.smart.srse.scope.ScopeDimension;
import gov.rajasthan.smart.srse.scope.ScopeLevel;
import gov.rajasthan.smart.srse.scope.ScopeNode;
import gov.rajasthan.smart.srse.scope.ScopeNodeRepository;
import gov.rajasthan.smart.srse.scope.UserScopeAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/** Scoped admin cannot grant nodes outside their subtree — Part 3 rule 1 via {@link UserAdminService}. */
@ExtendWith(MockitoExtension.class)
class UserAdminGrantScopeTest {

    @Mock
    private AppUserRepository userRepository;
    @Mock
    private AppRoleRepository roleRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private UserScopeAssignmentRepository scopeAssignmentRepository;
    @Mock
    private ScopeNodeRepository scopeNodeRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserScopeAssignmentService assignmentLoader;

    private AdminAuthorizationService authorization;
    private UserAdminService userAdminService;

    private AppUser admin;
    private AppUser target;
    private ScopeNode alwar;

    @BeforeEach
    void setUp() {
        authorization = new AdminAuthorizationService(userRoleRepository, assignmentLoader);
        userAdminService = new UserAdminService(
                userRepository,
                roleRepository,
                userRoleRepository,
                scopeAssignmentRepository,
                scopeNodeRepository,
                passwordEncoder,
                authorization);
        admin = user(1L, "admin");
        target = user(2L, "target");
        ScopeDimension geo = new ScopeDimension("GEO", "Geo", 1);
        setId(geo, 1L);
        ScopeLevel level = new ScopeLevel(geo, 1, "L1");
        alwar = node(11L, geo, level, "/G/ALWAR/");
    }

    @Test
    void scopedAdminCannotReplaceScopesOutsideSubtree() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(scopeNodeRepository.findAllById(List.of(11L))).thenReturn(List.of(alwar));
        when(userRoleRepository.findRoleCodesByUserId(1L)).thenReturn(List.of(AppRole.ADMIN));
        when(assignmentLoader.assignmentPathsByDimension(1L)).thenReturn(Map.of(1L, List.of("/G/JAIPUR/")));
        // The target must sit INSIDE the admin's subtree for this test to be
        // about rule 1 at all. With no assignments it is rejected earlier, by
        // the rule 2 guard that stops a scoped admin reaching a user who
        // belongs to no subtree — including the unassigned SuperAdmin.
        when(assignmentLoader.assignmentPathsByDimension(2L))
                .thenReturn(Map.of(1L, List.of("/G/JAIPUR/SANGANER/")));

        assertThrows(AdminAccessDeniedException.class, () -> userAdminService.replaceScopes(
                admin, 2L, new UserAdminDtos.ReplaceScopesRequest(List.of(11L))));
    }

    private static AppUser user(long id, String name) {
        AppUser u = new AppUser(name, "hash");
        setId(u, id);
        return u;
    }

    private static ScopeNode node(long id, ScopeDimension d, ScopeLevel level, String path) {
        ScopeNode n = new ScopeNode(d, level, null, "c", "n", path);
        setId(n, id);
        return n;
    }

    private static void setId(Object entity, long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
