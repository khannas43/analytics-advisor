package gov.rajasthan.smart.srse.metadata.sync;

import gov.rajasthan.smart.srse.datasource.ExternalDataSourceMetadataService;
import gov.rajasthan.smart.srse.lakehouse.LakehouseBrowseService;
import gov.rajasthan.smart.srse.lakehouse.RegisteredTable;
import gov.rajasthan.smart.srse.lakehouse.RegisteredTableRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

@Service
public class MetadataSyncService {

    private static final Logger log = LoggerFactory.getLogger(MetadataSyncService.class);
    private static final Duration STALE_RUNNING = Duration.ofMinutes(120);

    private final RegisteredTableRepository registrations;
    private final LakehouseBrowseService browse;
    private final ExternalDataSourceMetadataService externalMetadata;
    private final MetadataSyncRunRepository runs;
    private final MetadataSyncTableResultRepository tableResults;
    private final TableColumnSnapshotRepository snapshots;
    private final Executor metadataSyncExecutor;

    public MetadataSyncService(
            RegisteredTableRepository registrations,
            LakehouseBrowseService browse,
            ExternalDataSourceMetadataService externalMetadata,
            MetadataSyncRunRepository runs,
            MetadataSyncTableResultRepository tableResults,
            TableColumnSnapshotRepository snapshots,
            @org.springframework.beans.factory.annotation.Qualifier("metadataSyncExecutor")
            Executor metadataSyncExecutor) {
        this.registrations = registrations;
        this.browse = browse;
        this.externalMetadata = externalMetadata;
        this.runs = runs;
        this.tableResults = tableResults;
        this.snapshots = snapshots;
        this.metadataSyncExecutor = metadataSyncExecutor;
    }

    public record SyncStartResult(Long runId, boolean skippedOverlapping) {
    }

    /** Starts a full sync asynchronously; returns immediately with the run id when accepted. */
    public SyncStartResult startSyncAll(MetadataSyncTrigger trigger) {
        Optional<Long> skipped = findOverlappingRun();
        if (skipped.isPresent()) {
            return new SyncStartResult(skipped.get(), true);
        }
        Long runId = createRunningRun(trigger);
        metadataSyncExecutor.execute(() -> executeRun(runId));
        return new SyncStartResult(runId, false);
    }

    public void scheduleSyncAfterRegistration(RegisteredTable table) {
        if (table == null) {
            return;
        }
        Runnable task = () -> syncOneTableBestEffort(table, MetadataSyncTrigger.REGISTRATION);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    metadataSyncExecutor.execute(task);
                }
            });
        } else {
            metadataSyncExecutor.execute(task);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncOneTableBestEffort(RegisteredTable table, MetadataSyncTrigger trigger) {
        try {
            Long runId = createRunningRun(trigger);
            boolean failed = !syncTableInRun(runId, table);
            finishRun(runId, failed ? MetadataSyncOutcome.DEGRADED : MetadataSyncOutcome.SUCCESS);
        } catch (RuntimeException ex) {
            log.warn("Registration metadata sync failed for {} — registration unaffected",
                    table.toQualifiedTable().qualifiedName(), ex);
        }
    }

    public void executeScheduledSync() {
        startSyncAll(MetadataSyncTrigger.SCHEDULED);
    }

    void executeRun(long runId) {
        boolean anyFailed = false;
        for (RegisteredTable table : registrations.findAll()) {
            if (!syncTableInRun(runId, table)) {
                anyFailed = true;
            }
        }
        finishRun(runId, anyFailed ? MetadataSyncOutcome.DEGRADED : MetadataSyncOutcome.SUCCESS);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Long createRunningRun(MetadataSyncTrigger trigger) {
        MetadataSyncRun run = runs.save(new MetadataSyncRun(
                Instant.now(), trigger, MetadataSyncOutcome.RUNNING));
        return run.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void finishRun(long runId, MetadataSyncOutcome outcome) {
        runs.findById(runId).ifPresent(run -> {
            run.setFinishedAt(Instant.now());
            run.setOverallOutcome(outcome);
            runs.save(run);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    boolean syncTableInRun(long runId, RegisteredTable table) {
        String catalog = table.getCatalogName();
        String schema = table.getSchemaName();
        String tableName = table.getTableName();
        try {
            List<LakehouseBrowseService.ColumnInfo> columns = introspect(table);
            Instant syncedAt = Instant.now();
            snapshots.deleteAllForTable(catalog, schema, tableName);
            int ordinal = 0;
            for (LakehouseBrowseService.ColumnInfo column : columns) {
                snapshots.save(new TableColumnSnapshot(
                        catalog, schema, tableName,
                        column.name(), column.dataType(),
                        ordinal++, syncedAt, runId));
            }
            tableResults.save(new MetadataSyncTableResult(
                    runId, catalog, schema, tableName, MetadataTableOutcome.SUCCESS, null));
            return true;
        } catch (RuntimeException ex) {
            log.warn("Metadata sync failed for {}.{}: {}",
                    catalog, schema, tableName, ex.getMessage());
            tableResults.save(new MetadataSyncTableResult(
                    runId, catalog, schema, tableName, MetadataTableOutcome.FAILED,
                    MetadataSnapshotSupport.redactSyncError(ex)));
            return false;
        }
    }

    List<LakehouseBrowseService.ColumnInfo> introspect(RegisteredTable registration) {
        if (registration.isExternal()) {
            if (externalMetadata == null) {
                throw new IllegalStateException("External metadata service is unavailable");
            }
            return externalMetadata.registeredColumns(
                            registration.getExternalDataSourceId(),
                            registration.getExternalCatalog(),
                            registration.getSchemaName(),
                            registration.getTableName())
                    .stream()
                    .map(c -> new LakehouseBrowseService.ColumnInfo(c.name(), c.dataType()))
                    .toList();
        }
        return new ArrayList<>(browse.listColumns(
                registration.getCatalogName(),
                registration.getSchemaName(),
                registration.getTableName()));
    }

    Optional<Long> findOverlappingRun() {
        return runs.findFirstByOverallOutcomeOrderByStartedAtDesc(MetadataSyncOutcome.RUNNING)
                .filter(run -> run.getStartedAt().isAfter(Instant.now().minus(STALE_RUNNING)))
                .map(MetadataSyncRun::getId);
    }

    public Optional<MetadataSyncRun> latestRun() {
        return runs.findFirstByOrderByStartedAtDesc();
    }

    public List<MetadataSyncTableResult> resultsForRun(long runId) {
        return tableResults.findByRunIdOrderByCatalogNameAscSchemaNameAscTableNameAsc(runId);
    }
}
