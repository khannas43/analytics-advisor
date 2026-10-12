package gov.rajasthan.smart.srse.metadata.sync;

import gov.rajasthan.smart.srse.audit.AuditActionType;
import gov.rajasthan.smart.srse.audit.AuditService;
import gov.rajasthan.smart.srse.identity.AdminAuthorizationService;
import gov.rajasthan.smart.srse.identity.AppUser;
import gov.rajasthan.smart.srse.identity.AuthenticatedUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/metadata")
public class MetadataAdminController {

    private final MetadataSyncService syncService;
    private final MetadataManifestService manifestService;
    private final AdminAuthorizationService authorization;
    private final AuthenticatedUserService users;
    private final AuditService auditService;

    public MetadataAdminController(
            MetadataSyncService syncService,
            MetadataManifestService manifestService,
            AdminAuthorizationService authorization,
            AuthenticatedUserService users,
            AuditService auditService) {
        this.syncService = syncService;
        this.manifestService = manifestService;
        this.authorization = authorization;
        this.users = users;
        this.auditService = auditService;
    }

    @PostMapping("/sync")
    public ResponseEntity<SyncAcceptedResponse> syncNow() {
        AppUser actor = requireSuperAdmin();
        MetadataSyncService.SyncStartResult started = syncService.startSyncAll(MetadataSyncTrigger.MANUAL);
        auditService.recordRegistryEvent(
                AuditActionType.METADATA_SYNC_MANUAL,
                actor,
                "metadata-sync",
                started.skippedOverlapping()
                        ? "skipped overlapping run " + started.runId()
                        : "started run " + started.runId());
        return ResponseEntity.status(started.skippedOverlapping() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(new SyncAcceptedResponse(started.runId(), started.skippedOverlapping()));
    }

    @GetMapping("/status")
    public MetadataStatusResponse status() {
        requireSuperAdmin();
        return MetadataStatusResponse.from(syncService.latestRun().orElse(null), syncService);
    }

    @GetMapping(value = "/manifest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> manifest() {
        AppUser actor = requireSuperAdmin();
        String json = manifestService.buildManifestJson();
        auditService.recordRegistryEvent(
                AuditActionType.METADATA_MANIFEST_EXPORT,
                actor,
                "metadata-manifest",
                "download");
        return ResponseEntity.ok(json);
    }

    private AppUser requireSuperAdmin() {
        AppUser actor = users.requireCurrentUser();
        authorization.assertSuperAdminOnly(actor);
        return actor;
    }

    public record SyncAcceptedResponse(long runId, boolean skippedOverlapping) {
    }

    public record MetadataStatusResponse(
            Long runId,
            MetadataSyncTrigger trigger,
            MetadataSyncOutcome outcome,
            String startedAt,
            String finishedAt,
            List<TableResultView> tables) {

        static MetadataStatusResponse from(MetadataSyncRun run, MetadataSyncService syncService) {
            if (run == null) {
                return new MetadataStatusResponse(null, null, null, null, null, List.of());
            }
            List<TableResultView> tables = syncService.resultsForRun(run.getId()).stream()
                    .map(r -> new TableResultView(
                            r.getCatalogName(),
                            r.getSchemaName(),
                            r.getTableName(),
                            r.getTableOutcome(),
                            r.getErrorMessage()))
                    .toList();
            return new MetadataStatusResponse(
                    run.getId(),
                    run.getTrigger(),
                    run.getOverallOutcome(),
                    run.getStartedAt() != null ? run.getStartedAt().toString() : null,
                    run.getFinishedAt() != null ? run.getFinishedAt().toString() : null,
                    tables);
        }
    }

    public record TableResultView(
            String catalog,
            String schema,
            String table,
            MetadataTableOutcome outcome,
            String errorMessage) {
    }
}
