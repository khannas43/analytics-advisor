package gov.rajasthan.smart.srse.savedquery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "saved_query_grant")
public class SavedQueryGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saved_query_id", nullable = false)
    private Long savedQueryId;

    @Column(name = "grantee_user_id", nullable = false)
    private Long granteeUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public Long getSavedQueryId() {
        return savedQueryId;
    }

    public void setSavedQueryId(Long savedQueryId) {
        this.savedQueryId = savedQueryId;
    }

    public Long getGranteeUserId() {
        return granteeUserId;
    }

    public void setGranteeUserId(Long granteeUserId) {
        this.granteeUserId = granteeUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
