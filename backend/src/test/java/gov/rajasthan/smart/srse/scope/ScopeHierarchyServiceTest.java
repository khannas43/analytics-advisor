package gov.rajasthan.smart.srse.scope;

import gov.rajasthan.smart.srse.identity.AdminAuthorizationService;
import gov.rajasthan.smart.srse.identity.AppUser;
import gov.rajasthan.smart.srse.identity.UserScopeAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScopeHierarchyServiceTest {

    @Mock
    private ScopeDimensionRepository dimensionRepository;
    @Mock
    private ScopeLevelRepository levelRepository;
    @Mock
    private ScopeNodeRepository nodeRepository;
    @Mock
    private UserScopeAssignmentRepository assignmentRepository;
    @Mock
    private AdminAuthorizationService authorization;

    @InjectMocks
    private ScopeHierarchyService hierarchyService;

    @Test
    void deleteRefusedWhenDescendantsExist() {
        AppUser admin = new AppUser("admin", "x");
        ScopeDimension d = new ScopeDimension("G", "G", 1);
        ScopeLevel level = new ScopeLevel(d, 1, "L");
        ScopeNode node = new ScopeNode(d, level, null, "G", "G", "/G/");
        when(nodeRepository.findById(1L)).thenReturn(Optional.of(node));
        when(authorization.isSuperAdmin(admin)).thenReturn(true);
        when(nodeRepository.countByParentId(1L)).thenReturn(2L);

        assertThrows(IllegalStateException.class, () -> hierarchyService.deleteNode(admin, 1L));
    }

    @Test
    void deleteRefusedWhenAssignmentsExist() {
        AppUser admin = new AppUser("admin", "x");
        ScopeDimension d = new ScopeDimension("G", "G", 1);
        ScopeLevel level = new ScopeLevel(d, 1, "L");
        ScopeNode node = new ScopeNode(d, level, null, "G", "G", "/G/");
        when(nodeRepository.findById(1L)).thenReturn(Optional.of(node));
        when(authorization.isSuperAdmin(admin)).thenReturn(true);
        when(nodeRepository.countByParentId(1L)).thenReturn(0L);
        when(assignmentRepository.countByScopeNodeId(1L)).thenReturn(3L);

        assertThrows(IllegalStateException.class, () -> hierarchyService.deleteNode(admin, 1L));
    }
}
