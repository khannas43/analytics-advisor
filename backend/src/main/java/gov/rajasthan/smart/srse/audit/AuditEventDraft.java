package gov.rajasthan.smart.srse.audit;

public record AuditEventDraft(
        AuditActionType actionType,
        AuditOutcome outcome,
        Long actorUserId,
        Long targetUserId,
        String detail,
        String targetTables,
        String queryShape,
        String scopeSummary) {
}
