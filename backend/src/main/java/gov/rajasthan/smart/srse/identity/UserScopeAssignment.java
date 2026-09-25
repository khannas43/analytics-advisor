package gov.rajasthan.smart.srse.identity;

import gov.rajasthan.smart.srse.scope.ScopeNode;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_scope_assignment")
public class UserScopeAssignment {

    @EmbeddedId
    private UserScopeAssignmentId id = new UserScopeAssignmentId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("scopeNodeId")
    @JoinColumn(name = "scope_node_id", nullable = false)
    private ScopeNode scopeNode;

    protected UserScopeAssignment() {
    }

    public UserScopeAssignment(AppUser user, ScopeNode scopeNode) {
        this.user = user;
        this.scopeNode = scopeNode;
    }

    @PrePersist
    void assignId() {
        this.id = new UserScopeAssignmentId(user.getId(), scopeNode.getId());
    }

    public UserScopeAssignmentId getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public ScopeNode getScopeNode() {
        return scopeNode;
    }
}
