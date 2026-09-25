package gov.rajasthan.smart.srse.identity;

import gov.rajasthan.smart.srse.audit.AuditActionType;
import gov.rajasthan.smart.srse.audit.AuditService;
import gov.rajasthan.smart.srse.identity.admin.UserAdminDtos;
import gov.rajasthan.smart.srse.scope.ScopeNode;
import gov.rajasthan.smart.srse.scope.ScopeNodeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class UserAdminService {

    private final AppUserRepository userRepository;
    private final AppRoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserScopeAssignmentRepository scopeAssignmentRepository;
    private final ScopeNodeRepository scopeNodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuthorizationService authorization;
    private final AuditService auditService;

    public UserAdminService(
            AppUserRepository userRepository,
            AppRoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            UserScopeAssignmentRepository scopeAssignmentRepository,
            ScopeNodeRepository scopeNodeRepository,
            PasswordEncoder passwordEncoder,
            AdminAuthorizationService authorization,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.scopeAssignmentRepository = scopeAssignmentRepository;
        this.scopeNodeRepository = scopeNodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.authorization = authorization;
        this.auditService = auditService;
    }

    public List<UserAdminDtos.UserSummary> listUsers(AppUser caller) {
        List<UserAdminDtos.UserSummary> result = new ArrayList<>();
        for (AppUser user : userRepository.findAllByOrderByUsernameAsc()) {
            if (authorization.canManageUser(caller, user)) {
                result.add(toSummary(user));
            }
        }
        return result;
    }

    public UserAdminDtos.UserSummary getUser(AppUser caller, long id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        authorization.assertCanManageUser(caller, user);
        return toSummary(user);
    }

    @Transactional
    public UserAdminDtos.UserSummary createUser(AppUser caller, UserAdminDtos.CreateUserRequest req) {
        if (userRepository.findByUsernameIgnoreCase(req.username()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        List<String> roles = normalizeRoles(req.roles());
        authorization.assertCanGrantRolesOnCreate(caller, roles);

        AppUser user = new AppUser(req.username().trim(), passwordEncoder.encode(req.initialPassword()));
        user.setEmail(trimToNull(req.email()));
        user.setMobile(trimToNull(req.mobile()));
        user.setMustChangePassword(true);
        user.setPasswordChangedAt(Instant.now());
        if (req.mfaRequired() != null) {
            user.setMfaRequired(req.mfaRequired());
        }
        authorization.assertMfaDeliverable(user, user.isMfaRequired());
        user = userRepository.save(user);

        replaceRoles(user, roles);
        if (req.scopeNodeIds() != null && !req.scopeNodeIds().isEmpty()) {
            replaceScopeAssignments(caller, user, req.scopeNodeIds(), false);
            auditService.recordAdminUserEvent(
                    AuditActionType.SCOPE_GRANTED, caller, user, "Initial scope on create");
        }
        auditService.recordAdminUserEvent(
                AuditActionType.USER_CREATED, caller, user, "username=" + user.getUsername());
        auditService.recordAdminUserEvent(
                AuditActionType.ROLE_GRANTED, caller, user, String.join(",", roles));
        auditAuditReaderGrantRevoke(caller, user, List.of(), roles);
        return toSummary(user);
    }

    @Transactional
    public UserAdminDtos.UserSummary updateUser(AppUser caller, long id, UserAdminDtos.UpdateUserRequest req) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        authorization.assertCanManageUser(caller, user);
        authorization.assertNotSelfRoleOrScopeEdit(caller, user);

        List<String> roles = normalizeRoles(req.roles());
        authorization.assertCanChangeRoles(caller, user, roles);
        authorization.assertCanDemoteOrRemoveSuperAdmin(user, roles);

        applyContactUpdate(caller, user, req);
        if (req.mfaRequired() != null) {
            user.setMfaRequired(req.mfaRequired());
        }
        authorization.assertMfaDeliverable(user, user.isMfaRequired());
        user.touchUpdatedAt();
        userRepository.save(user);
        List<String> beforeRoles = authorization.roleCodes(user.getId());
        replaceRoles(user, roles);
        auditService.recordAdminUserEvent(
                AuditActionType.USER_UPDATED, caller, user, "username=" + user.getUsername());
        auditService.recordAdminUserEvent(
                AuditActionType.ROLE_GRANTED, caller, user, String.join(",", roles));
        auditAuditReaderGrantRevoke(caller, user, beforeRoles, roles);
        return toSummary(user);
    }

    @Transactional
    public UserAdminDtos.UserSummary replaceScopes(
            AppUser caller, long id, UserAdminDtos.ReplaceScopesRequest req) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        authorization.assertCanManageUser(caller, user);
        authorization.assertNotSelfRoleOrScopeEdit(caller, user);
        replaceScopeAssignments(caller, user, req.scopeNodeIds() == null ? List.of() : req.scopeNodeIds(), true);
        auditService.recordAdminUserEvent(
                AuditActionType.SCOPE_GRANTED, caller, user, "Scope assignments replaced");
        return toSummary(user);
    }

    @Transactional
    public UserAdminDtos.UserSummary deactivate(AppUser caller, long id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        authorization.assertCanManageUser(caller, user);
        authorization.assertCanDeactivate(caller, user);
        user.setActive(false);
        user.incrementSessionVersion();
        user.touchUpdatedAt();
        userRepository.save(user);
        auditService.recordAdminUserEvent(
                AuditActionType.USER_DEACTIVATED, caller, user, "username=" + user.getUsername());
        return toSummary(user);
    }

    @Transactional
    public UserAdminDtos.UserSummary reactivate(AppUser caller, long id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        authorization.assertCanManageUser(caller, user);
        user.setActive(true);
        user.touchUpdatedAt();
        userRepository.save(user);
        return toSummary(user);
    }

    @Transactional
    public void resetPassword(AppUser caller, long id, UserAdminDtos.ResetPasswordRequest req) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        authorization.assertCanManageUser(caller, user);
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(true);
        user.setPasswordChangedAt(Instant.now());
        user.incrementSessionVersion();
        user.touchUpdatedAt();
        userRepository.save(user);
        auditService.recordAdminUserEvent(
                AuditActionType.PASSWORD_RESET, caller, user, "username=" + user.getUsername());
    }

    private void replaceScopeAssignments(
            AppUser caller, AppUser target, List<Long> nodeIds, boolean replaceExisting) {
        List<ScopeNode> nodes = scopeNodeRepository.findAllById(nodeIds);
        if (nodes.size() != new HashSet<>(nodeIds).size()) {
            throw new IllegalArgumentException("Unknown scope node id");
        }
        authorization.assertCanGrantScopeNodes(caller, nodes);
        if (replaceExisting) {
            scopeAssignmentRepository.deleteByUserId(target.getId());
        }
        Set<Long> existing = new HashSet<>();
        if (!replaceExisting) {
            scopeAssignmentRepository.findAllWithNodeByUserId(target.getId()).forEach(a ->
                    existing.add(a.getScopeNode().getId()));
        }
        for (ScopeNode node : nodes) {
            if (!existing.contains(node.getId())) {
                scopeAssignmentRepository.save(new UserScopeAssignment(target, node));
            }
        }
    }

    private void replaceRoles(AppUser user, List<String> roleCodes) {
        userRoleRepository.deleteByUserId(user.getId());
        for (String code : roleCodes) {
            AppRole role = roleRepository.findByCode(code)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + code));
            userRoleRepository.save(new UserRole(user, role));
        }
    }

    private UserAdminDtos.UserSummary toSummary(AppUser user) {
        List<String> roles = authorization.roleCodes(user.getId());
        List<UserAdminDtos.ScopeAssignmentView> scopes = scopeAssignmentRepository
                .findAllWithNodeByUserId(user.getId()).stream()
                .map(a -> new UserAdminDtos.ScopeAssignmentView(
                        a.getScopeNode().getId(),
                        a.getScopeNode().getDimension().getId(),
                        a.getScopeNode().getDimension().getCode(),
                        a.getScopeNode().getPath(),
                        a.getScopeNode().getName()))
                .toList();
        return new UserAdminDtos.UserSummary(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getMobile(),
                user.isActive(),
                user.isMfaRequired(),
                user.isMustChangePassword(),
                user.isEmailVerified(),
                user.isMobileVerified(),
                roles,
                scopes,
                user.getCreatedAt(),
                user.getLastLoginAt());
    }

    private static List<String> normalizeRoles(List<String> roles) {
        if (roles == null) {
            return List.of();
        }
        return roles.stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }

    private void applyContactUpdate(AppUser caller, AppUser user, UserAdminDtos.UpdateUserRequest req) {
        String newEmail = trimToNull(req.email());
        String newMobile = trimToNull(req.mobile());
        if (!Objects.equals(user.getEmail(), newEmail)) {
            user.setEmail(newEmail);
            user.setEmailVerified(false);
            auditService.recordUserContactChange(caller, user, "EMAIL", "Admin updated email");
        }
        if (!Objects.equals(user.getMobile(), newMobile)) {
            user.setMobile(newMobile);
            user.setMobileVerified(false);
            auditService.recordUserContactChange(caller, user, "MOBILE", "Admin updated mobile");
        }
        if (req.emailVerified() != null) {
            user.setEmailVerified(req.emailVerified());
        }
        if (req.mobileVerified() != null) {
            user.setMobileVerified(req.mobileVerified());
        }
    }

    private void auditAuditReaderGrantRevoke(
            AppUser caller, AppUser target, List<String> before, List<String> after) {
        boolean had = before.contains(AppRole.AUDIT_READER);
        boolean has = after.contains(AppRole.AUDIT_READER);
        if (!had && has) {
            auditService.recordAdminUserEvent(
                    AuditActionType.ROLE_GRANTED,
                    caller,
                    target,
                    AppRole.AUDIT_READER + " granted");
        } else if (had && !has) {
            auditService.recordAdminUserEvent(
                    AuditActionType.ROLE_GRANTED,
                    caller,
                    target,
                    AppRole.AUDIT_READER + " revoked");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
