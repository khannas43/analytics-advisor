package gov.rajasthan.smart.srse.audit;

import java.time.Instant;
import java.util.Set;

/** Filters for the audit viewer and export — scoped query is applied separately (§7.3.8). */
public record AuditSearchCriteria(
        Instant occurredFrom,
        Instant occurredTo,
        Long actorUserId,
        AuditActionType actionType,
        AuditOutcome outcome,
        Set<Long> visibleActorIds,
        boolean superAdminReader) {

    public boolean scopedToActors() {
        return !superAdminReader;
    }
}
