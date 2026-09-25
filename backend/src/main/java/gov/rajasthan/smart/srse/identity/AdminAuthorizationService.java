package gov.rajasthan.smart.srse.identity;

import gov.rajasthan.smart.srse.scope.ScopeAccessEvaluator;
import gov.rajasthan.smart.srse.scope.ScopeNode;
import gov.rajasthan.smart.srse.scope.UserScopeAssignmentService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Part 3 authorization for admin APIs (7.1.5 / 7.1.6). Enforced server-side;
 * UI hiding alone is not sufficient.
 */
@Service
public class AdminAuthorizationService {

    private final UserRoleRepository userRoleRepository;
    private final UserScopeAssignmentService assignmentLoader;

    public AdminAuthorizationService(
            UserRoleRepository userRoleRepository,
            UserScopeAssignmentService assignmentLoader) {
        this.userRoleRepository = userRoleRepository;
        this.assignmentLoader = assignmentLoader;
    }

    public boolean isSuperAdmin(AppUser user) {
        return userRoleRepository.findRoleCodesByUserId(user.getId()).contains(AppRole.SUPER_ADMIN);
    }

    public boolean hasAuditRead(AppUser user) {
        return isSuperAdmin(user) || roleCodes(user.getId()).contains(AppRole.AUDIT_READER);
    }

    /**
     * §7.3.8 — an entry's actor is visible to a reader only under the same subtree
     * containment as {@link #canManageUser} (rule 2). SuperAdmin callers bypass.
     */
    public boolean readerMaySeeActor(AppUser reader, AppUser actor) {
        if (isSuperAdmin(reader)) {
            return true;
        }
        return canManageUser(reader, actor);
    }

    public List<String> roleCodes(long userId) {
        return userRoleRepository.findRoleCodesByUserId(userId);
    }

    public Map<Long, List<String>> assignmentPaths(long userId) {
        return assignmentLoader.assignmentPathsByDimension(userId);
    }

    /** Rule 1 — admin may grant only nodes within their own subtree; SuperAdmin exempt. */
    public void assertCanGrantScopeNodes(AppUser caller, List<ScopeNode> nodes) {
        if (isSuperAdmin(caller)) {
            return;
        }
        Map<Long, List<String>> callerPaths = assignmentPaths(caller.getId());
        for (ScopeNode node : nodes) {
            if (!callerCoversNodePath(callerPaths, node)) {
                throw new AdminAccessDeniedException(
                        "Cannot grant scope outside your subtree: " + node.getPath());
            }
        }
    }

    /**
     * Rule 2 — manageable only if every assignment the target holds is within the
     * caller's subtree.
     *
     * <p><b>Two guards below are not refinements; without them this method grants
     * a full takeover.</b> The loop tests the TARGET's assignments, so a target
     * holding none satisfies "every assignment is inside my subtree"
     * vacuously — and the bootstrap SuperAdmin holds none. A district admin
     * could therefore manage the SuperAdmin, and while role edits and
     * deactivation were blocked elsewhere, {@code reset-password} was not:
     * observed end to end, a scoped admin set the SuperAdmin's password and
     * logged in as them.
     *
     * <p>So: a target with no assignments belongs to no subtree and is
     * SuperAdmin-only, and a target holding SUPER_ADMIN is never manageable by
     * anyone else whatever the scopes say. Either alone stops the escalation;
     * both are kept because the first is easy to reason away and the second is
     * the one that stays true if the scope model changes.
     */
    public boolean canManageUser(AppUser caller, AppUser target) {
        if (caller.getId().equals(target.getId())) {
            return true;
        }
        if (isSuperAdmin(caller)) {
            return true;
        }
        if (roleCodes(target.getId()).contains(AppRole.SUPER_ADMIN)) {
            return false;
        }
        Map<Long, List<String>> callerPaths = assignmentPaths(caller.getId());
        Map<Long, List<String>> targetPaths = assignmentPaths(target.getId());
        if (targetPaths.isEmpty()) {
            return false;
        }
        for (Map.Entry<Long, List<String>> entry : targetPaths.entrySet()) {
            long dimensionId = entry.getKey();
            for (String targetPath : entry.getValue()) {
                if (!callerCoversDataPath(callerPaths, dimensionId, targetPath)) {
                    return false;
                }
            }
        }
        return true;
    }

    public void assertCanManageUser(AppUser caller, AppUser target) {
        if (!canManageUser(caller, target)) {
            throw new AdminAccessDeniedException("User is outside your administrative scope");
        }
    }

    public void assertCanGrantRolesOnCreate(AppUser caller, List<String> proposedRoleCodes) {
        if (proposedRoleCodes.contains(AppRole.SUPER_ADMIN) && !isSuperAdmin(caller)) {
            throw new AdminAccessDeniedException("Only a SuperAdmin may grant or revoke SuperAdmin role");
        }
        assertAuditReaderRoleChange(caller, null, List.of(), proposedRoleCodes);
    }

    /** Rule 3 — only SuperAdmin may grant or revoke SUPER_ADMIN. */
    public void assertCanChangeRoles(AppUser caller, AppUser target, List<String> proposedRoleCodes) {
        List<String> current = roleCodes(target.getId());
        boolean hadSuper = current.contains(AppRole.SUPER_ADMIN);
        boolean willHaveSuper = proposedRoleCodes.contains(AppRole.SUPER_ADMIN);
        if (hadSuper != willHaveSuper && !isSuperAdmin(caller)) {
            throw new AdminAccessDeniedException("Only a SuperAdmin may grant or revoke SuperAdmin role");
        }
        assertAuditReaderRoleChange(caller, target, current, proposedRoleCodes);
    }

    /** Grant/revoke {@link AppRole#AUDIT_READER} — subtree + no self-grant; grantor must already read audit. */
    public void assertAuditReaderRoleChange(
            AppUser caller, AppUser target, List<String> currentRoles, List<String> proposedRoleCodes) {
        boolean had = currentRoles.contains(AppRole.AUDIT_READER);
        boolean will = proposedRoleCodes.contains(AppRole.AUDIT_READER);
        if (had == will) {
            return;
        }
        if (!hasAuditRead(caller)) {
            throw new AdminAccessDeniedException(
                    "Only an audit reader may grant or revoke " + AppRole.AUDIT_READER);
        }
        if (target != null) {
            assertNotSelfRoleOrScopeEdit(caller, target);
            assertCanManageUser(caller, target);
        }
    }

    /** Rule 4 — nobody may edit their own roles or scopes (SuperAdmin included). */
    public void assertNotSelfRoleOrScopeEdit(AppUser caller, AppUser target) {
        if (caller.getId().equals(target.getId())) {
            throw new AdminAccessDeniedException("You cannot change your own roles or scope assignments");
        }
    }

    /** Rule 5 — no self-deactivation; last active SuperAdmin cannot be demoted or deactivated. */
    public void assertCanDeactivate(AppUser caller, AppUser target) {
        if (caller.getId().equals(target.getId())) {
            throw new AdminAccessDeniedException("You cannot deactivate your own account");
        }
        assertLastSuperAdminProtected(target, false);
    }

    public void assertCanDemoteOrRemoveSuperAdmin(AppUser target, List<String> proposedRoleCodes) {
        if (roleCodes(target.getId()).contains(AppRole.SUPER_ADMIN)
                && !proposedRoleCodes.contains(AppRole.SUPER_ADMIN)) {
            assertLastSuperAdminProtected(target, true);
        }
    }

    private void assertLastSuperAdminProtected(AppUser target, boolean demotion) {
        if (!roleCodes(target.getId()).contains(AppRole.SUPER_ADMIN)) {
            return;
        }
        long others = userRoleRepository.countActiveUsersWithRoleExcluding(
                AppRole.SUPER_ADMIN, target.getId());
        if (others == 0 && target.isActive()) {
            String action = demotion ? "demote" : "deactivate";
            throw new IllegalStateException(
                    "Cannot " + action + " the last active SuperAdmin — bootstrap would be unrecoverable");
        }
    }

    /** Rule 6 — MFA requires a verified delivery channel (7.1a). */
    public void assertMfaDeliverable(AppUser user, boolean mfaRequired) {
        if (!mfaRequired) {
            return;
        }
        if (!user.isEmailVerified() && !user.isMobileVerified()) {
            throw new IllegalArgumentException(
                    "MFA requires at least one verified email or mobile contact (7.1a)");
        }
    }

    public boolean callerCoversNodePath(Map<Long, List<String>> callerPaths, ScopeNode node) {
        return callerCoversDataPath(callerPaths, node.getDimension().getId(), node.getPath());
    }

    public boolean callerCoversDataPath(Map<Long, List<String>> callerPaths, long dimensionId, String dataPath) {
        List<String> assigned = callerPaths.get(dimensionId);
        if (assigned == null || assigned.isEmpty()) {
            return false;
        }
        for (String callerPath : assigned) {
            if (ScopeAccessEvaluator.assignmentCoversDataPath(callerPath, dataPath)) {
                return true;
            }
        }
        return false;
    }

    public void assertSuperAdminOnly(AppUser caller) {
        if (!isSuperAdmin(caller)) {
            throw new AdminAccessDeniedException("SuperAdmin only");
        }
    }

    /** Node create: Admin may create under a parent they hold; SuperAdmin anywhere. */
    public void assertCanCreateNodeUnderParent(AppUser caller, ScopeNode parent) {
        if (isSuperAdmin(caller)) {
            return;
        }
        if (parent == null) {
            throw new AdminAccessDeniedException("Only a SuperAdmin may create root scope nodes");
        }
        Map<Long, List<String>> callerPaths = assignmentPaths(caller.getId());
        if (!callerCoversNodePath(callerPaths, parent)) {
            throw new AdminAccessDeniedException("Cannot create a node under a scope you do not hold");
        }
    }
}
