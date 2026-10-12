package gov.rajasthan.smart.srse.metadata.sync;

import gov.rajasthan.smart.srse.lakehouse.LakehouseBrowseService;
import gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService;
import gov.rajasthan.smart.srse.lakehouse.OfficerRegistryScopeService;
import gov.rajasthan.smart.srse.lakehouse.QualifiedColumn;
import gov.rajasthan.smart.srse.lakehouse.QualifiedTable;
import gov.rajasthan.smart.srse.lakehouse.RegisteredTable;
import gov.rajasthan.smart.srse.lakehouse.RegisteredTableRepository;
import gov.rajasthan.smart.srse.lakehouse.TableScopePolicy;
import gov.rajasthan.smart.srse.metadata.AnalysisColumnMetadata;
import gov.rajasthan.smart.srse.metadata.AnalysisColumnMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetadataSnapshotTest {

    private static final String CATALOG = "iceberg";
    private static final String SCHEMA = "srse";
    private static final String TABLE = "beneficiary";

    @Mock
    private RegisteredTableRepository registrations;
    @Mock
    private AnalysisColumnMetadataRepository columnMetadata;
    @Mock
    private LakehouseBrowseService browse;
    @Mock
    private OfficerRegistryScopeService officerScope;
    @Mock
    private gov.rajasthan.smart.srse.lakehouse.RegisteredTableScopeCatalog scopeCatalog;
    @Mock
    private gov.rajasthan.smart.srse.lakehouse.TableScopeRegistrationService tableScopeRegistrationService;
    @Mock
    private gov.rajasthan.smart.srse.identity.AuthenticatedUserService authenticatedUserService;
    @Mock
    private gov.rajasthan.smart.srse.audit.AuditService auditService;
    @Mock
    private TableColumnSnapshotRepository snapshots;
    @Mock
    private MetadataSyncRunRepository runs;
    @Mock
    private MetadataSyncTableResultRepository tableResults;

    private MetadataSnapshotService snapshotService;
    private MetadataSyncProperties properties;
    private LakehouseRegistryService registry;

    @BeforeEach
    void setUp() {
        properties = new MetadataSyncProperties(null, new MetadataSyncProperties.Snapshot(7));
        snapshotService = new MetadataSnapshotService(snapshots, runs, properties);
        MetadataSyncService syncService = new MetadataSyncService(
                registrations,
                browse,
                null,
                runs,
                tableResults,
                snapshots,
                new SyncTaskExecutor());
        registry = new LakehouseRegistryService(
                registrations,
                columnMetadata,
                browse,
                officerScope,
                scopeCatalog,
                tableScopeRegistrationService,
                authenticatedUserService,
                auditService,
                null,
                null,
                snapshotService,
                syncService);
        lenient().when(officerScope.currentOfficerScope())
                .thenReturn(TableScopePolicy.OfficerScopeView.bypass());
        lenient().when(scopeCatalog.metadataFor(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new TableScopePolicy.TableScopeMetadata(false, List.of(), java.util.Set.of()));
        lenient().when(authenticatedUserService.requireCurrentUser())
                .thenReturn(new gov.rajasthan.smart.srse.identity.AppUser("officer", "hash"));
        stubRegistered();
    }

    @Test
    void describeColumnsUsesLiveTypeNotSnapshot() {
        when(browse.listColumns(CATALOG, SCHEMA, TABLE)).thenReturn(List.of(
                new LakehouseBrowseService.ColumnInfo("age_years", "bigint")));
        Map<String, LakehouseRegistryService.RegisteredColumn> described =
                registry.describeColumns(new QualifiedTable(CATALOG, SCHEMA, TABLE), List.of("age_years"));
        assertEquals("bigint", described.get("age_years").dataType());
        verify(browse).listColumns(CATALOG, SCHEMA, TABLE);
    }

    @Test
    void displayUsesSnapshotWhenFreshWithoutCallingBrowse() {
        when(snapshots.findByCatalogNameAndSchemaNameAndTableNameOrderByOrdinalPositionAsc(
                CATALOG, SCHEMA, TABLE)).thenReturn(List.of(
                new TableColumnSnapshot(CATALOG, SCHEMA, TABLE, "district", "varchar", 0,
                        Instant.now(), 1L)));
        List<LakehouseRegistryService.RegisteredColumn> cols =
                registry.listColumnsForDisplay(CATALOG, SCHEMA, TABLE);
        assertEquals("district", cols.get(0).name());
        verify(browse, never()).listColumns(CATALOG, SCHEMA, TABLE);
    }

    @Test
    void expiredSnapshotFallsBackToLive() {
        when(snapshots.findByCatalogNameAndSchemaNameAndTableNameOrderByOrdinalPositionAsc(
                CATALOG, SCHEMA, TABLE)).thenReturn(List.of(
                new TableColumnSnapshot(CATALOG, SCHEMA, TABLE, "district", "varchar", 0,
                        Instant.now().minus(10, ChronoUnit.DAYS), 1L)));
        when(browse.listColumns(CATALOG, SCHEMA, TABLE)).thenReturn(List.of(
                new LakehouseBrowseService.ColumnInfo("district", "varchar")));
        registry.listColumnsForDisplay(CATALOG, SCHEMA, TABLE);
        verify(browse).listColumns(CATALOG, SCHEMA, TABLE);
    }

    @Test
    void hiddenColumnAbsentImmediatelyDespiteFreshSnapshot() {
        when(snapshots.findByCatalogNameAndSchemaNameAndTableNameOrderByOrdinalPositionAsc(
                CATALOG, SCHEMA, TABLE)).thenReturn(List.of(
                new TableColumnSnapshot(CATALOG, SCHEMA, TABLE, "secret", "varchar", 0, Instant.now(), 1L),
                new TableColumnSnapshot(CATALOG, SCHEMA, TABLE, "district", "varchar", 1, Instant.now(), 1L)));
        AnalysisColumnMetadata hidden = org.mockito.Mockito.mock(AnalysisColumnMetadata.class);
        when(hidden.getColumnName()).thenReturn("secret");
        when(hidden.isVisible()).thenReturn(false);
        when(columnMetadata.findByCatalogNameAndSchemaNameAndTableName(CATALOG, SCHEMA, TABLE))
                .thenReturn(List.of(hidden));
        List<String> names = registry.listColumnsForDisplay(CATALOG, SCHEMA, TABLE).stream()
                .map(LakehouseRegistryService.RegisteredColumn::name)
                .toList();
        assertFalse(names.contains("secret"));
        assertTrue(names.contains("district"));
    }

    @Test
    void outOfScopeTableRefusedEvenWhenSnapshotExists() {
        when(officerScope.currentOfficerScope())
                .thenReturn(new TableScopePolicy.OfficerScopeView(false, Map.of()));
        when(scopeCatalog.metadataFor(org.mockito.ArgumentMatchers.any(RegisteredTable.class)))
                .thenReturn(new TableScopePolicy.TableScopeMetadata(false, List.of(), java.util.Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> registry.listColumnsForDisplay(CATALOG, SCHEMA, TABLE));
    }

    @Test
    void unregisterDeletesSnapshotRows() {
        RegisteredTable row = new RegisteredTable(1L, CATALOG, SCHEMA, TABLE, "GOLD");
        when(registrations.findById(1L)).thenReturn(Optional.of(row));
        registry.unregister(1L);
        verify(snapshots).deleteAllForTable(CATALOG, SCHEMA, TABLE);
        verify(registrations).deleteById(1L);
    }

    @Test
    void droppedColumnStillInDisplayButValidateFailsAtRunTime() {
        when(snapshots.findByCatalogNameAndSchemaNameAndTableNameOrderByOrdinalPositionAsc(
                CATALOG, SCHEMA, TABLE)).thenReturn(List.of(
                new TableColumnSnapshot(CATALOG, SCHEMA, TABLE, "dropped_col", "varchar", 0, Instant.now(), 1L)));
        when(browse.listColumns(CATALOG, SCHEMA, TABLE)).thenReturn(List.of());
        assertFalse(registry.listColumnsForDisplay(CATALOG, SCHEMA, TABLE).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> registry.validateColumn(new QualifiedColumn(
                        new QualifiedTable(CATALOG, SCHEMA, TABLE), "dropped_col")));
    }

    private void stubRegistered() {
        RegisteredTable row = new RegisteredTable(1L, CATALOG, SCHEMA, TABLE, "GOLD");
        lenient().when(registrations.findByCatalogNameAndSchemaNameAndTableName(CATALOG, SCHEMA, TABLE))
                .thenReturn(Optional.of(row));
        lenient().when(columnMetadata.findByCatalogNameAndSchemaNameAndTableName(CATALOG, SCHEMA, TABLE))
                .thenReturn(List.of());
    }
}
