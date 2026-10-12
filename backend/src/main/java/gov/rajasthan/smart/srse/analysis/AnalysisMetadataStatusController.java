package gov.rajasthan.smart.srse.analysis;

import gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService;
import gov.rajasthan.smart.srse.metadata.sync.MetadataSnapshotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisMetadataStatusController {

    private final LakehouseRegistryService registry;
    private final MetadataSnapshotService snapshots;

    public AnalysisMetadataStatusController(
            LakehouseRegistryService registry,
            MetadataSnapshotService snapshots) {
        this.registry = registry;
        this.snapshots = snapshots;
    }

    @GetMapping("/metadata-status")
    public MetadataStatusView metadataStatus(
            @RequestParam String catalog,
            @RequestParam String schema,
            @RequestParam String table) {
        registry.validateRegistered(catalog, schema, table);
        registry.requireVisibleRegistration(catalog, schema, table);
        var fresh = snapshots.freshSnapshot(catalog, schema, table);
        if (fresh.isPresent()) {
            Instant syncedAt = fresh.get().syncedAt();
            return new MetadataStatusView(syncedAt.toString(), "SNAPSHOT", snapshots.clusterDegraded());
        }
        return new MetadataStatusView(null, "LIVE", snapshots.clusterDegraded());
    }

    public record MetadataStatusView(String syncedAt, String source, boolean degraded) {
    }
}
