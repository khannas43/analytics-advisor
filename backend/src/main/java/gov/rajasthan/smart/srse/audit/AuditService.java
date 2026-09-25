package gov.rajasthan.smart.srse.audit;

import gov.rajasthan.smart.srse.identity.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditCaptureService capture;
    private final AuditScopeSummaryService scopeSummary;

    public AuditService(AuditCaptureService capture, AuditScopeSummaryService scopeSummary) {
        this.capture = capture;
        this.scopeSummary = scopeSummary;
    }

    @Transactional
    public void recordUserContactChange(AppUser actor, AppUser target, String field, String detail) {
        capture.recordRequired(new AuditEventDraft(
                AuditActionType.CONTACT_CHANGED,
                AuditOutcome.SUCCESS,
                actor == null ? null : actor.getId(),
                target.getId(),
                detail == null ? field : detail,
                null,
                null,
                scopeSummary.summarizeCurrentOfficer()));
    }

    public void recordAuthEvent(
            AuditActionType action,
            AuditOutcome outcome,
            Long actorUserId,
            Long targetUserId,
            String detail) {
        capture.recordBestEffort(new AuditEventDraft(
                action,
                outcome,
                actorUserId,
                targetUserId,
                detail,
                null,
                null,
                scopeSummary.summarizeCurrentOfficer()));
    }

    @Transactional
    public void recordAdminUserEvent(
            AuditActionType action,
            AppUser actor,
            AppUser target,
            String detail) {
        capture.recordRequired(new AuditEventDraft(
                action,
                AuditOutcome.SUCCESS,
                actor == null ? null : actor.getId(),
                target == null ? null : target.getId(),
                detail,
                null,
                null,
                scopeSummary.summarizeCurrentOfficer()));
    }

    @Transactional
    public void recordRegistryEvent(
            AuditActionType action,
            AppUser actor,
            String qualifiedTable,
            String detail) {
        capture.recordRequired(new AuditEventDraft(
                action,
                AuditOutcome.SUCCESS,
                actor == null ? null : actor.getId(),
                null,
                detail,
                qualifiedTable,
                null,
                scopeSummary.summarizeCurrentOfficer()));
    }

    @Transactional
    public void recordScopeBindingChange(AppUser actor, String qualifiedTable, String detail) {
        capture.recordRequired(new AuditEventDraft(
                AuditActionType.SCOPE_BINDING_CHANGED,
                AuditOutcome.SUCCESS,
                actor.getId(),
                null,
                detail,
                qualifiedTable,
                null,
                scopeSummary.summarizeCurrentOfficer()));
    }

    @Transactional
    public void recordSharedReferenceSet(AppUser actor, String qualifiedTable, boolean shared) {
        capture.recordRequired(new AuditEventDraft(
                AuditActionType.SHARED_REFERENCE_SET,
                AuditOutcome.SUCCESS,
                actor.getId(),
                null,
                shared ? "sharedReference=true" : "sharedReference=false",
                qualifiedTable,
                null,
                scopeSummary.summarizeCurrentOfficer()));
    }

    public void recordLogout(AppUser user) {
        capture.recordBestEffort(new AuditEventDraft(
                AuditActionType.LOGOUT,
                AuditOutcome.SUCCESS,
                user.getId(),
                user.getId(),
                "logout",
                null,
                null,
                scopeSummary.summarizeCurrentOfficer()));
    }

    @Transactional
    public void recordGatewayEvent(
            AuditActionType action,
            AppUser actor,
            AuditOutcome outcome,
            String detail) {
        capture.recordRequired(new AuditEventDraft(
                action,
                outcome,
                actor.getId(),
                null,
                detail,
                null,
                null,
                scopeSummary.summarizeCurrentOfficer()));
    }
}
