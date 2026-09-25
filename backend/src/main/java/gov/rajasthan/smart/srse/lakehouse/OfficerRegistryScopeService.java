package gov.rajasthan.smart.srse.lakehouse;

import gov.rajasthan.smart.srse.identity.AdminAuthorizationService;
import gov.rajasthan.smart.srse.identity.AppUser;
import gov.rajasthan.smart.srse.identity.AppUserRepository;
import gov.rajasthan.smart.srse.identity.UserScopeAssignment;
import gov.rajasthan.smart.srse.identity.UserScopeAssignmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Resolves the current officer's scope view for registry filtering (§7.2.2a). */
@Service
public class OfficerRegistryScopeService {

    private final AppUserRepository userRepository;
    private final UserScopeAssignmentRepository assignmentRepository;
    private final AdminAuthorizationService adminAuthorization;
    private final boolean localAuthMode;
    private final boolean mockAuthMode;

    public OfficerRegistryScopeService(
            AppUserRepository userRepository,
            UserScopeAssignmentRepository assignmentRepository,
            AdminAuthorizationService adminAuthorization,
            @Value("${srse.auth-mode:mock}") String authMode) {
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.adminAuthorization = adminAuthorization;
        this.localAuthMode = "local".equalsIgnoreCase(authMode);
        this.mockAuthMode = "mock".equalsIgnoreCase(authMode);
    }

    /**
     * <p><b>Every unresolved case denies rather than bypasses.</b> This method
     * previously fell back to {@link TableScopePolicy.OfficerScopeView#bypass()}
     * three ways — no authentication, no matching user row, and any auth mode
     * that is not {@code local} — so an identity the service could not place
     * received unrestricted access to every registered table. The last of those
     * is the one that would have shipped: the product's own plan has SSO
     * arriving after local accounts, and the day {@code auth-mode} stops being
     * {@code local}, scoping would have switched itself off with nothing
     * failing.
     *
     * <p>{@code mock} remains an explicit bypass because it has no user model to
     * scope against and exists only for local development — the same reason
     * CLAUDE.md calls it a seam and not an ACL. It is named here rather than
     * reached by default, so adding a third mode denies instead of opening.
     */
    public TableScopePolicy.OfficerScopeView currentOfficerScope() {
        if (!localAuthMode) {
            return mockAuthMode
                    ? TableScopePolicy.OfficerScopeView.bypass()
                    : TableScopePolicy.OfficerScopeView.denyScoped();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return TableScopePolicy.OfficerScopeView.denyScoped();
        }
        return userRepository.findByUsernameIgnoreCase(authentication.getName())
                .map(this::scopeForUser)
                .orElseGet(TableScopePolicy.OfficerScopeView::denyScoped);
    }

    private TableScopePolicy.OfficerScopeView scopeForUser(AppUser user) {
        if (adminAuthorization.isSuperAdmin(user)) {
            return TableScopePolicy.OfficerScopeView.bypass();
        }
        List<UserScopeAssignment> assignments = assignmentRepository.findAllWithNodeByUserId(user.getId());
        Map<Long, List<TableScopePolicy.AssignmentNode>> byDimension = new HashMap<>();
        for (UserScopeAssignment assignment : assignments) {
            long dimensionId = assignment.getScopeNode().getDimension().getId();
            int depth = assignment.getScopeNode().getLevel().getDepth();
            byDimension.computeIfAbsent(dimensionId, k -> new ArrayList<>())
                    .add(new TableScopePolicy.AssignmentNode(depth));
        }
        return new TableScopePolicy.OfficerScopeView(false, Map.copyOf(byDimension));
    }
}
