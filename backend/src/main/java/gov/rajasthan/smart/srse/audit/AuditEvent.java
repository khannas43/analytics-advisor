package gov.rajasthan.smart.srse.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Append-only audit row (§7.3 / A9). No update or delete API — insert once when
 * the outcome is known.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 64)
    private AuditActionType actionType;

    @Column(length = 1024)
    private String detail;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private AuditOutcome outcome;

    @Column(name = "target_tables", length = 2048)
    private String targetTables;

    @Column(name = "query_shape", length = 8192)
    private String queryShape;

    @Column(name = "scope_summary", length = 512)
    private String scopeSummary;

    protected AuditEvent() {
    }

    AuditEvent(
            Instant occurredAt,
            Long actorUserId,
            Long targetUserId,
            AuditActionType actionType,
            AuditOutcome outcome,
            String detail,
            String sourceIp,
            String targetTables,
            String queryShape,
            String scopeSummary) {
        this.occurredAt = occurredAt;
        this.actorUserId = actorUserId;
        this.targetUserId = targetUserId;
        this.actionType = actionType;
        this.outcome = outcome;
        this.detail = detail;
        this.sourceIp = sourceIp;
        this.targetTables = targetTables;
        this.queryShape = queryShape;
        this.scopeSummary = scopeSummary;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public AuditActionType getActionType() {
        return actionType;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public String getDetail() {
        return detail;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public String getTargetTables() {
        return targetTables;
    }

    public String getQueryShape() {
        return queryShape;
    }

    public String getScopeSummary() {
        return scopeSummary;
    }
}
