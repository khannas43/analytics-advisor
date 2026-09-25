package gov.rajasthan.smart.srse.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Single choke point for audit writes (§7.3). */
@Service
public class AuditCaptureService {

    private static final Logger log = LoggerFactory.getLogger(AuditCaptureService.class);

    private final AuditEventRepository repository;
    private final ClientIpResolver clientIpResolver;
    private final AuditDegradedState degradedState;

    public AuditCaptureService(
            AuditEventRepository repository,
            ClientIpResolver clientIpResolver,
            AuditDegradedState degradedState) {
        this.repository = repository;
        this.clientIpResolver = clientIpResolver;
        this.degradedState = degradedState;
    }

    /** Admin mutations — same transaction as the change; failure rolls back. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordRequired(AuditEventDraft draft) {
        persist(draft);
    }

    /** Export — must commit before streaming; refusal if write fails. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordExportRequired(AuditEventDraft draft) {
        persist(draft);
    }

    /** Query execution / preview — proceed on failure but alarm. */
    public void recordBestEffort(AuditEventDraft draft) {
        try {
            persistInNewTransaction(draft);
        } catch (RuntimeException ex) {
            degradedState.recordFailure();
            log.error("{} audit write failed for {} — query proceeds but log may be incomplete",
                    AuditDegradedState.FAILURE_MARKER, draft.actionType(), ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void persistInNewTransaction(AuditEventDraft draft) {
        persist(draft);
    }

    private void persist(AuditEventDraft draft) {
        repository.save(new AuditEvent(
                Instant.now(),
                draft.actorUserId(),
                draft.targetUserId(),
                draft.actionType(),
                draft.outcome(),
                draft.detail(),
                clientIpResolver.resolveClientIp(),
                draft.targetTables(),
                draft.queryShape(),
                draft.scopeSummary()));
    }
}
