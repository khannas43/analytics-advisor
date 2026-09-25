package gov.rajasthan.smart.srse.audit.admin;

import gov.rajasthan.smart.srse.audit.AuditActionType;
import gov.rajasthan.smart.srse.audit.AuditEvent;
import gov.rajasthan.smart.srse.audit.AuditOutcome;

import java.time.Instant;

public record AuditEventView(
        long id,
        Instant occurredAt,
        Long actorUserId,
        String actorUsername,
        Long targetUserId,
        AuditActionType actionType,
        AuditOutcome outcome,
        String detail,
        String sourceIp,
        String targetTables,
        String queryShape,
        String scopeSummary) {

    public static AuditEventView from(AuditEvent event, String actorUsername) {
        return new AuditEventView(
                event.getId(),
                event.getOccurredAt(),
                event.getActorUserId(),
                actorUsername,
                event.getTargetUserId(),
                event.getActionType(),
                event.getOutcome(),
                event.getDetail(),
                event.getSourceIp(),
                event.getTargetTables(),
                event.getQueryShape(),
                event.getScopeSummary());
    }
}
