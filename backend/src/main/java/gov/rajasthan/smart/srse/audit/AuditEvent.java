package gov.rajasthan.smart.srse.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

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

    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType;

    @Column(length = 1024)
    private String detail;

    protected AuditEvent() {
    }

    public AuditEvent(Instant occurredAt, Long actorUserId, Long targetUserId, String actionType, String detail) {
        this.occurredAt = occurredAt;
        this.actorUserId = actorUserId;
        this.targetUserId = targetUserId;
        this.actionType = actionType;
        this.detail = detail;
    }
}
