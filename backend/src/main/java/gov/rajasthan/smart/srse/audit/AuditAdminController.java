package gov.rajasthan.smart.srse.audit;

import gov.rajasthan.smart.srse.audit.admin.AuditEventView;
import gov.rajasthan.smart.srse.audit.admin.AuditPageResponse;
import gov.rajasthan.smart.srse.audit.AuditWriteFailureException;
import gov.rajasthan.smart.srse.identity.AppUser;
import gov.rajasthan.smart.srse.identity.AuthenticatedUserService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AuditAdminController {

    private final AuditViewerService viewerService;
    private final AuditCaptureService auditCapture;
    private final AuditScopeSummaryService scopeSummary;
    private final AuthenticatedUserService authenticatedUserService;

    public AuditAdminController(
            AuditViewerService viewerService,
            AuditCaptureService auditCapture,
            AuditScopeSummaryService scopeSummary,
            AuthenticatedUserService authenticatedUserService) {
        this.viewerService = viewerService;
        this.auditCapture = auditCapture;
        this.scopeSummary = scopeSummary;
        this.authenticatedUserService = authenticatedUserService;
    }

    @GetMapping("/audit")
    public AuditPageResponse list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) AuditActionType actionType,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        AppUser reader = authenticatedUserService.requireCurrentUser();
        return viewerService.search(reader, from, to, actorUserId, actionType, outcome, page, size);
    }

    /**
     * Streams CSV using the same scoped query as {@link #list}. The export audit row
     * is committed before the first byte (§7.3 Q6) — it does not recurse into further
     * export rows when someone downloads the log.
     */
    @GetMapping(value = "/audit.csv", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) AuditActionType actionType,
            @RequestParam(required = false) AuditOutcome outcome) {
        AppUser reader = authenticatedUserService.requireCurrentUser();
        try {
            auditCapture.recordExportRequired(new AuditEventDraft(
                    AuditActionType.EXPORT,
                    AuditOutcome.SUCCESS,
                    scopeSummary.currentActorUserId(),
                    null,
                    "Audit log export",
                    null,
                    null,
                    scopeSummary.summarizeCurrentOfficer()));
        } catch (RuntimeException ex) {
            throw new AuditWriteFailureException("Export audit row could not be written", ex);
        }
        List<AuditEventView> rows = viewerService.searchForExport(
                reader, from, to, actorUserId, actionType, outcome);
        StreamingResponseBody body = outputStream -> {
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
            writer.write('\uFEFF');
            writer.write("id,occurred_at,actor_user_id,actor_username,action_type,outcome,detail,source_ip,"
                    + "target_tables,query_shape,scope_summary");
            writer.write("\r\n");
            for (AuditEventView row : rows) {
                writer.write(csvLine(row));
                writer.write("\r\n");
            }
            writer.flush();
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-log.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    private static String csvLine(AuditEventView row) {
        return String.join(",",
                csvField(String.valueOf(row.id())),
                csvField(row.occurredAt() == null ? "" : row.occurredAt().toString()),
                csvField(row.actorUserId() == null ? "" : row.actorUserId().toString()),
                csvField(row.actorUsername()),
                csvField(row.actionType() == null ? "" : row.actionType().name()),
                csvField(row.outcome() == null ? "" : row.outcome().name()),
                csvField(row.detail()),
                csvField(row.sourceIp()),
                csvField(row.targetTables()),
                csvField(row.queryShape()),
                csvField(row.scopeSummary()));
    }

    private static String csvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
