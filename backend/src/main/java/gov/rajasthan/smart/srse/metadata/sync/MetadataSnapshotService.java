package gov.rajasthan.smart.srse.metadata.sync;

import gov.rajasthan.smart.srse.lakehouse.LakehouseBrowseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Read path for officer-facing column display (global snapshot, not scoped). */
@Service
public class MetadataSnapshotService {

    private final TableColumnSnapshotRepository snapshots;
    private final MetadataSyncRunRepository runs;
    private final MetadataSyncProperties properties;

    public MetadataSnapshotService(
            TableColumnSnapshotRepository snapshots,
            MetadataSyncRunRepository runs,
            MetadataSyncProperties properties) {
        this.snapshots = snapshots;
        this.runs = runs;
        this.properties = properties;
    }

    public record SnapshotColumn(String name, String dataType, int ordinalPosition) {
    }

    public record TableSnapshotView(Instant syncedAt, List<SnapshotColumn> columns) {
    }

    public Optional<TableSnapshotView> freshSnapshot(String catalog, String schema, String table) {
        List<TableColumnSnapshot> rows =
                snapshots.findByCatalogNameAndSchemaNameAndTableNameOrderByOrdinalPositionAsc(
                        catalog, schema, table);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Instant syncedAt = rows.get(0).getSyncedAt();
        if (!MetadataSnapshotSupport.isFresh(syncedAt, properties.snapshot().ttlDays())) {
            return Optional.empty();
        }
        List<SnapshotColumn> columns = rows.stream()
                .map(r -> new SnapshotColumn(r.getColumnName(), r.getDataType(), r.getOrdinalPosition()))
                .toList();
        return Optional.of(new TableSnapshotView(syncedAt, columns));
    }

    public boolean clusterDegraded() {
        return runs.findFirstByOrderByStartedAtDesc()
                .map(run -> run.getOverallOutcome() == MetadataSyncOutcome.DEGRADED
                        || run.getOverallOutcome() == MetadataSyncOutcome.FAILED)
                .orElse(false);
    }

    @Transactional
    public void deleteSnapshotsForTable(String catalog, String schema, String table) {
        snapshots.deleteAllForTable(catalog, schema, table);
    }

    public List<LakehouseBrowseService.ColumnInfo> toColumnInfos(List<SnapshotColumn> columns) {
        return columns.stream()
                .map(c -> new LakehouseBrowseService.ColumnInfo(c.name(), c.dataType()))
                .toList();
    }
}
