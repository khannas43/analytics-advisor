package gov.rajasthan.smart.srse.audit;

public record AuditEventDraft(
        AuditActionType actionType,
        AuditOutcome outcome,
        Long actorUserId,
        Long targetUserId,
        String detail,
        String targetTables,
        String queryShape,
        String scopeSummary,
        Long durationMs) {

    /** Backward-compatible constructor for existing call sites (no duration). */
    public AuditEventDraft(
            AuditActionType actionType,
            AuditOutcome outcome,
            Long actorUserId,
            Long targetUserId,
            String detail,
            String targetTables,
            String queryShape,
            String scopeSummary) {
        this(actionType, outcome, actorUserId, targetUserId, detail, targetTables, queryShape, scopeSummary, null);
    }

    public AuditEventDraft withDurationMs(long durationMs) {
        return new AuditEventDraft(
                actionType, outcome, actorUserId, targetUserId, detail, targetTables, queryShape, scopeSummary,
                durationMs);
    }
}
