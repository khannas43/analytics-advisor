package gov.rajasthan.smart.srse.audit;

import gov.rajasthan.smart.srse.identity.AdminAuthorizationService;
import gov.rajasthan.smart.srse.identity.AppUser;
import gov.rajasthan.smart.srse.identity.AppUserRepository;
import gov.rajasthan.smart.srse.identity.AppRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** §7.3.8 scoped visibility — reuses rule 2, including the no-assignment guard. */
@ExtendWith(MockitoExtension.class)
class AuditViewerScopeTest {

    private static final long GEO = 1L;

    @Mock
    private AuditEventQueryDao queryDao;
    @Mock
    private AppUserRepository userRepository;
    @Mock
    private gov.rajasthan.smart.srse.identity.UserRoleRepository userRoleRepository;
    @Mock
    private gov.rajasthan.smart.srse.scope.UserScopeAssignmentService assignmentLoader;

    private AdminAuthorizationService authorization;
    private AuditViewerService viewerService;
    private AppUser scopedReader;
    private AppUser officerInSubtree;
    private AppUser officerOutside;
    private AppUser superAdmin;
    private AppUser unassignedOfficer;

    @BeforeEach
    void setUp() {
        authorization = new AdminAuthorizationService(userRoleRepository, assignmentLoader);
        viewerService = new AuditViewerService(
                queryDao,
                userRepository,
                authorization,
                new AuditProperties(List.of(), 365));
        scopedReader = user(10L, "jaipuradmin");
        officerInSubtree = user(11L, "jaipurofficer");
        officerOutside = user(12L, "alwarofficer");
        superAdmin = user(1L, "superadmin");
        unassignedOfficer = user(13L, "floater");
    }

    @Test
    void scopedReaderSeesInSubtreeActorNotOutside() {
        stubRoles(scopedReader, AppRole.ADMIN, AppRole.AUDIT_READER);
        stubRoles(officerInSubtree, AppRole.OFFICER);
        stubRoles(officerOutside, AppRole.OFFICER);
        stubRoles(superAdmin, AppRole.SUPER_ADMIN);
        stubAssignments(scopedReader, Map.of(GEO, List.of("/G/JAIPUR/")));
        stubAssignments(officerInSubtree, Map.of(GEO, List.of("/G/JAIPUR/SANGANER/")));
        stubAssignments(officerOutside, Map.of(GEO, List.of("/G/ALWAR/")));
        when(userRepository.findAllByOrderByUsernameAsc()).thenReturn(List.of(
                scopedReader, officerInSubtree, officerOutside, superAdmin));

        Set<Long> visible = viewerService.visibleActiveActorIds(scopedReader);
        assertTrue(visible.contains(officerInSubtree.getId()));
        assertTrue(visible.contains(scopedReader.getId()));
        assertFalse(visible.contains(officerOutside.getId()));
        assertFalse(visible.contains(superAdmin.getId()));
    }

    @Test
    void actorWithNoAssignmentsNotVisibleToScopedReader() {
        stubRoles(scopedReader, AppRole.ADMIN, AppRole.AUDIT_READER);
        stubRoles(unassignedOfficer, AppRole.OFFICER);
        stubAssignments(scopedReader, Map.of(GEO, List.of("/G/JAIPUR/")));
        stubAssignments(unassignedOfficer, Map.of());
        when(userRepository.findAllByOrderByUsernameAsc()).thenReturn(List.of(scopedReader, unassignedOfficer));

        assertFalse(viewerService.visibleActiveActorIds(scopedReader).contains(unassignedOfficer.getId()));
    }

    @Test
    void superAdminCriteriaDoesNotScopeActors() {
        stubRoles(superAdmin, AppRole.SUPER_ADMIN);
        AuditSearchCriteria criteria = viewerService.buildCriteria(
                superAdmin, null, null, null, null, null);
        assertFalse(criteria.scopedToActors());
    }

    @Test
    void deactivatedActorExcludedFromVisibleSet() {
        lenientStubRoles(scopedReader, AppRole.ADMIN, AppRole.AUDIT_READER);
        lenientStubRoles(officerInSubtree, AppRole.OFFICER);
        lenientStubAssignments(scopedReader, Map.of(GEO, List.of("/G/JAIPUR/")));
        lenientStubAssignments(officerInSubtree, Map.of(GEO, List.of("/G/JAIPUR/")));
        officerInSubtree.setActive(false);
        when(userRepository.findAllByOrderByUsernameAsc()).thenReturn(List.of(scopedReader, officerInSubtree));

        assertFalse(viewerService.visibleActiveActorIds(scopedReader).contains(officerInSubtree.getId()));
    }

    private void stubRoles(AppUser user, String... roles) {
        when(userRoleRepository.findRoleCodesByUserId(user.getId())).thenReturn(List.of(roles));
    }

    private void stubAssignments(AppUser user, Map<Long, List<String>> paths) {
        when(assignmentLoader.assignmentPathsByDimension(user.getId())).thenReturn(paths);
    }

    private void lenientStubRoles(AppUser user, String... roles) {
        lenient().when(userRoleRepository.findRoleCodesByUserId(user.getId())).thenReturn(List.of(roles));
    }

    private void lenientStubAssignments(AppUser user, Map<Long, List<String>> paths) {
        lenient().when(assignmentLoader.assignmentPathsByDimension(user.getId())).thenReturn(paths);
    }

    private static AppUser user(long id, String name) {
        AppUser u = new AppUser(name, "hash");
        u.setPasswordChangedAt(Instant.now());
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
