package gov.rajasthan.smart.srse.identity;

import gov.rajasthan.smart.srse.scope.UserScopeAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditReaderRoleGrantTest {

    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private UserScopeAssignmentService assignmentLoader;

    private AdminAuthorizationService authorization;
    private AppUser scopedAdmin;
    private AppUser target;

    @BeforeEach
    void setUp() {
        authorization = new AdminAuthorizationService(userRoleRepository, assignmentLoader);
        scopedAdmin = user(2L);
        target = user(3L);
    }

    @Test
    void cannotSelfGrantAuditReader() {
        when(userRoleRepository.findRoleCodesByUserId(scopedAdmin.getId()))
                .thenReturn(List.of(AppRole.ADMIN, AppRole.AUDIT_READER));
        assertThrows(AdminAccessDeniedException.class, () -> authorization.assertAuditReaderRoleChange(
                scopedAdmin,
                scopedAdmin,
                List.of(AppRole.ADMIN),
                List.of(AppRole.ADMIN, AppRole.AUDIT_READER)));
    }

    @Test
    void adminWithoutAuditReadCannotGrantAuditReader() {
        when(userRoleRepository.findRoleCodesByUserId(scopedAdmin.getId())).thenReturn(List.of(AppRole.ADMIN));
        assertThrows(AdminAccessDeniedException.class, () -> authorization.assertAuditReaderRoleChange(
                scopedAdmin, target, List.of(AppRole.OFFICER), List.of(AppRole.OFFICER, AppRole.AUDIT_READER)));
    }

    @Test
    void auditReaderMayGrantWithinSubtree() {
        when(userRoleRepository.findRoleCodesByUserId(scopedAdmin.getId()))
                .thenReturn(List.of(AppRole.ADMIN, AppRole.AUDIT_READER));
        when(userRoleRepository.findRoleCodesByUserId(target.getId())).thenReturn(List.of(AppRole.OFFICER));
        when(assignmentLoader.assignmentPathsByDimension(scopedAdmin.getId()))
                .thenReturn(Map.of(1L, List.of("/G/JAIPUR/")));
        when(assignmentLoader.assignmentPathsByDimension(target.getId()))
                .thenReturn(Map.of(1L, List.of("/G/JAIPUR/SANGANER/")));

        authorization.assertAuditReaderRoleChange(
                scopedAdmin, target, List.of(AppRole.OFFICER), List.of(AppRole.OFFICER, AppRole.AUDIT_READER));
    }

    private static AppUser user(long id) {
        AppUser u = new AppUser("u" + id, "hash");
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
