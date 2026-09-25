package gov.rajasthan.smart.srse.analysis;

import gov.rajasthan.smart.srse.audit.AuditActionType;
import gov.rajasthan.smart.srse.audit.AuditCaptureService;
import gov.rajasthan.smart.srse.audit.AuditEventDraft;
import gov.rajasthan.smart.srse.audit.AuditOutcome;
import gov.rajasthan.smart.srse.audit.AuditScopeSummaryService;
import gov.rajasthan.smart.srse.audit.QueryShapeAudit;
import gov.rajasthan.smart.srse.analysis.TargetMatchSpec;
import org.springframework.stereotype.Service;

import java.util.StringJoiner;

/**
 * Analysis audit choke point (§7.3 / AA-11). Records {@code query.sql()} with
 * placeholders — never {@link RecordMatchService#renderQueryForDisplay}.
 */
@Service
public class AnalysisAuditService {

    private final RecordMatchService matchService;
    private final AuditCaptureService auditCapture;
    private final AuditScopeSummaryService scopeSummary;

    public AnalysisAuditService(
            RecordMatchService matchService,
            AuditCaptureService auditCapture,
            AuditScopeSummaryService scopeSummary) {
        this.matchService = matchService;
        this.auditCapture = auditCapture;
        this.scopeSummary = scopeSummary;
    }

    public RecordMatchService.MatchQuery planMatchAudited(RecordMatchRequest req) {
        return plan(req, AuditActionType.QUERY_EXECUTED);
    }

    public RecordMatchService.MatchQuery planPreviewAudited(RecordMatchRequest req) {
        return plan(req, AuditActionType.QUERY_PREVIEWED);
    }

    public void recordExport(RecordMatchRequest req, RecordMatchService.MatchQuery query) {
        auditCapture.recordExportRequired(buildDraft(
                AuditActionType.EXPORT,
                AuditOutcome.SUCCESS,
                req,
                query,
                "CSV export"));
    }

    public void recordMultiExport(MultiTargetRecordMatchRequest req, String combinedQueryShape) {
        auditCapture.recordExportRequired(new AuditEventDraft(
                AuditActionType.EXPORT,
                AuditOutcome.SUCCESS,
                scopeSummary.currentActorUserId(),
                null,
                "Multi-target CSV export",
                QueryShapeAudit.targetTablesFrom(req),
                QueryShapeAudit.queryShapeFromSql(combinedQueryShape),
                scopeSummary.summarizeCurrentOfficer()));
    }

    public void planMultiMatchAudited(MultiTargetRecordMatchRequest req) {
        try {
            StringJoiner shapes = new StringJoiner("\n---\n");
            if (req.targets() != null) {
                for (TargetMatchSpec target : req.targets()) {
                    RecordMatchService.MatchQuery query =
                            matchService.planMatch(MultiTargetRecordMatchService.toSingleMatch(req, target));
                    shapes.add(query.sql());
                }
            }
            auditCapture.recordBestEffort(new AuditEventDraft(
                    AuditActionType.QUERY_EXECUTED,
                    AuditOutcome.SUCCESS,
                    scopeSummary.currentActorUserId(),
                    null,
                    "multi-target match",
                    QueryShapeAudit.targetTablesFrom(req),
                    QueryShapeAudit.queryShapeFromSql(shapes.toString()),
                    scopeSummary.summarizeCurrentOfficer()));
        } catch (RuntimeException ex) {
            auditCapture.recordBestEffort(new AuditEventDraft(
                    AuditActionType.QUERY_REFUSED,
                    AuditOutcome.REFUSED,
                    scopeSummary.currentActorUserId(),
                    null,
                    truncate(ex.getMessage(), 512),
                    QueryShapeAudit.targetTablesFrom(req),
                    null,
                    scopeSummary.summarizeCurrentOfficer()));
            throw ex;
        }
    }

    private RecordMatchService.MatchQuery plan(RecordMatchRequest req, AuditActionType action) {
        try {
            RecordMatchService.MatchQuery query = matchService.planMatch(req);
            auditCapture.recordBestEffort(buildDraft(action, AuditOutcome.SUCCESS, req, query, null));
            return query;
        } catch (RuntimeException ex) {
            auditCapture.recordBestEffort(buildDraft(
                    AuditActionType.QUERY_REFUSED,
                    AuditOutcome.REFUSED,
                    req,
                    null,
                    truncate(ex.getMessage(), 512)));
            throw ex;
        }
    }

    private AuditEventDraft buildDraft(
            AuditActionType action,
            AuditOutcome outcome,
            RecordMatchRequest req,
            RecordMatchService.MatchQuery query,
            String detail) {
        return new AuditEventDraft(
                action,
                outcome,
                scopeSummary.currentActorUserId(),
                null,
                detail,
                QueryShapeAudit.targetTablesFrom(req),
                query == null ? null : QueryShapeAudit.queryShapeFromSql(query.sql()),
                scopeSummary.summarizeCurrentOfficer());
    }

    private static String truncate(String message, int max) {
        if (message == null) {
            return null;
        }
        return message.length() <= max ? message : message.substring(0, max);
    }
}
