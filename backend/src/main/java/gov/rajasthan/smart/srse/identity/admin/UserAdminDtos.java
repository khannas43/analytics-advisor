package gov.rajasthan.smart.srse.identity.admin;

import java.time.Instant;
import java.util.List;

public final class UserAdminDtos {

    private UserAdminDtos() {
    }

    public record ScopeAssignmentView(long scopeNodeId, long dimensionId, String dimensionCode,
                                      String path, String name) {
    }

    public record UserSummary(
            long id,
            String username,
            String email,
            String mobile,
            boolean active,
            boolean mfaRequired,
            boolean mustChangePassword,
            boolean emailVerified,
            boolean mobileVerified,
            List<String> roles,
            List<ScopeAssignmentView> scopeAssignments,
            Instant createdAt,
            Instant lastLoginAt) {
    }

    public record CreateUserRequest(
            String username,
            String email,
            String mobile,
            String initialPassword,
            List<String> roles,
            List<Long> scopeNodeIds,
            Boolean mfaRequired) {
    }

    public record UpdateUserRequest(
            String email,
            String mobile,
            List<String> roles,
            Boolean mfaRequired) {
    }

    public record ReplaceScopesRequest(List<Long> scopeNodeIds) {
    }

    public record ResetPasswordRequest(String newPassword) {
    }
}
