package gov.rajasthan.smart.srse.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class UserScopeAssignmentId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "scope_node_id")
    private Long scopeNodeId;

    protected UserScopeAssignmentId() {
    }

    public UserScopeAssignmentId(Long userId, Long scopeNodeId) {
        this.userId = userId;
        this.scopeNodeId = scopeNodeId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getScopeNodeId() {
        return scopeNodeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserScopeAssignmentId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(scopeNodeId, that.scopeNodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, scopeNodeId);
    }
}
