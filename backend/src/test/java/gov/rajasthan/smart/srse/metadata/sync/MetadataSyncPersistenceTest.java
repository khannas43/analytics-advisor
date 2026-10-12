package gov.rajasthan.smart.srse.metadata.sync;

import gov.rajasthan.smart.srse.lakehouse.LakehouseBrowseService;
import gov.rajasthan.smart.srse.lakehouse.RegisteredTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.DriverManager;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The metadata sync against a REAL operational database. {@link MetadataSnapshotTest} and
 * {@link MetadataSyncServiceTest} mock every repository, so they cannot see transaction behaviour:
 * the service's {@code @Transactional(REQUIRES_NEW)} helpers are called from inside the same class,
 * where Spring's proxy does not apply, and {@code deleteAllForTable} is a {@code @Modifying} query
 * that needs a transaction. This drives the production path (the post-registration hook, which runs
 * on the sync executor exactly as in the running app).
 *
 * <p>Uses the local operational PostgreSQL like {@code OperationalSchemaMatchesEntitiesTest}, and
 * skips when it is not up. Writes only under the throwaway catalog {@code zz_it} and removes it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIf("operationalDatabaseReachable")
class MetadataSyncPersistenceTest {

    private static final String URL = "jdbc:postgresql://localhost:5433/srse";
    private static final String USER = "srse";
    private static final String PASSWORD = "srse_local_pw";
    private static final String CATALOG = "zz_it";
    private static final String SCHEMA = "persist";
    private static final String TABLE = "t_sync";
    private static final String BURST_CATALOG = "zz_it_burst";

    static boolean operationalDatabaseReachable() {
        try (var ignored = DriverManager.getConnection(URL, USER, PASSWORD)) {
            return true;
        } catch (Exception e) {
            System.out.println("[sync persistence] skipped — no operational database at " + URL);
            return false;
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("srse.datasource.operational.jdbc-url", () -> URL);
        registry.add("srse.datasource.operational.username", () -> USER);
        registry.add("srse.datasource.operational.password", () -> PASSWORD);
        registry.add("srse.datasource.operational.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("srse.datasource.operational.initialization-fail-timeout", () -> "-1");
        registry.add("srse.datasource.analytical.initialization-fail-timeout", () -> "-1");
        registry.add("srse.auth-mode", () -> "mock");
        registry.add("srse.metadata.sync.enabled", () -> "false");
    }

    @MockBean
    private LakehouseBrowseService browse;
    @Autowired
    private MetadataSyncService sync;
    @Autowired
    private TableColumnSnapshotRepository snapshots;
    @Autowired
    private MetadataSyncTableResultRepository results;
    @Autowired
    private MetadataSyncRunRepository runs;

    @AfterEach
    void cleanUp() {
        var mine = results.findAll().stream()
                .filter(r -> CATALOG.equals(r.getCatalogName()) || BURST_CATALOG.equals(r.getCatalogName())).toList();
        snapshots.findAll().stream()
                .filter(s -> CATALOG.equals(s.getCatalogName()) || BURST_CATALOG.equals(s.getCatalogName()))
                .forEach(snapshots::delete);
        results.deleteAll(mine);
        Set<Long> runIds = mine.stream().map(MetadataSyncTableResult::getRunId).collect(Collectors.toSet());
        runs.deleteAllById(runIds);
    }

    private static RegisteredTable table() {
        return new RegisteredTable(null, CATALOG, SCHEMA, TABLE, "GOLD");
    }

    private List<TableColumnSnapshot> stored() {
        return snapshots.findByCatalogNameAndSchemaNameAndTableNameOrderByOrdinalPositionAsc(CATALOG, SCHEMA, TABLE);
    }

    private long resultCount(MetadataTableOutcome outcome) {
        return results.findAll().stream()
                .filter(r -> CATALOG.equals(r.getCatalogName()) && TABLE.equals(r.getTableName())
                        && r.getTableOutcome() == outcome).count();
    }

    private static void await(String what, BooleanSupplier done) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            if (done.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("timed out waiting for: " + what);
    }

    private void syncNow() {
        sync.scheduleSyncAfterRegistration(table());
    }

    @Test
    void aSuccessfulSyncPersistsTheColumnsInOrder() throws Exception {
        when(browse.listColumns(CATALOG, SCHEMA, TABLE)).thenReturn(List.of(
                new LakehouseBrowseService.ColumnInfo("id", "bigint"),
                new LakehouseBrowseService.ColumnInfo("district", "varchar")));

        syncNow();
        await("snapshot rows", () -> resultCount(MetadataTableOutcome.SUCCESS) + resultCount(MetadataTableOutcome.FAILED) > 0);

        assertEquals(0, resultCount(MetadataTableOutcome.FAILED),
                "the sync failed: " + results.findAll().stream()
                        .filter(r -> CATALOG.equals(r.getCatalogName()) && TABLE.equals(r.getTableName()))
                        .map(MetadataSyncTableResult::getErrorMessage).toList());
        assertEquals(List.of("id", "district"),
                stored().stream().map(TableColumnSnapshot::getColumnName).toList());
        assertEquals(List.of("bigint", "varchar"),
                stored().stream().map(TableColumnSnapshot::getDataType).toList());
    }

    @Test
    void aFailedSyncKeepsTheLastGoodSnapshotEvenWhenItFailsHalfWayThroughTheWrite() throws Exception {
        when(browse.listColumns(CATALOG, SCHEMA, TABLE)).thenReturn(List.of(
                new LakehouseBrowseService.ColumnInfo("id", "bigint"),
                new LakehouseBrowseService.ColumnInfo("district", "varchar")));
        syncNow();
        await("first sync", () -> resultCount(MetadataTableOutcome.SUCCESS) == 1);
        assertEquals(2, stored().size());

        // The second column name exceeds varchar(128): the INSERT fails AFTER the old rows were deleted.
        // Atomic replace-all means the old rows must still be there.
        when(browse.listColumns(CATALOG, SCHEMA, TABLE)).thenReturn(List.of(
                new LakehouseBrowseService.ColumnInfo("new_a", "bigint"),
                new LakehouseBrowseService.ColumnInfo("x".repeat(200), "varchar")));
        syncNow();
        await("failed sync recorded", () -> resultCount(MetadataTableOutcome.FAILED) == 1);

        assertEquals(List.of("id", "district"),
                stored().stream().map(TableColumnSnapshot::getColumnName).toList(),
                "the last good snapshot must survive a failed write");
    }

    @Test
    void aBurstOfRegistrationsBeyondTheExecutorCapacityDoesNotThrowToTheCaller() throws Exception {
        when(browse.listColumns(anyString(), anyString(), anyString())).thenReturn(List.of());
        int burst = 700; // more than the executor's queue, so some are rejected
        for (int i = 0; i < burst; i++) {
            // used to throw TaskRejectedException out of the registering request
            sync.scheduleSyncAfterRegistration(
                    new RegisteredTable(null, BURST_CATALOG, SCHEMA, "t_burst_" + i, "GOLD"));
        }
        // let the accepted ones finish so they cannot write after this test's cleanup
        await("burst to drain", () -> {
            long done = results.findAll().stream().filter(r -> BURST_CATALOG.equals(r.getCatalogName())).count();
            return done > 0 && done == lastCount(done);
        });
    }

    private long previous = -1;

    /** True-ish stability check: the same count twice in a row means the queue has gone quiet. */
    private long lastCount(long now) {
        long before = previous;
        previous = now;
        return before;
    }
}
