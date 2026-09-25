package gov.rajasthan.smart.srse.lakehouse;

import gov.rajasthan.smart.srse.metadata.AnalysisColumnMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Part 3 — officer-facing registry methods respect scope visibility (§7.2.2a). */
@ExtendWith(MockitoExtension.class)
class LakehouseRegistryScopeFilteringTest {

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
    private RegisteredTableScopeCatalog scopeCatalog;
    @Mock
    private TableScopeRegistrationService tableScopeRegistrationService;

    @Mock
    private gov.rajasthan.smart.srse.identity.AuthenticatedUserService authenticatedUserService;

    @Mock
    private gov.rajasthan.smart.srse.audit.AuditService auditService;

    private LakehouseRegistryService service;

    private RegisteredTable registered;

    @BeforeEach
    void setUp() {
        service = new LakehouseRegistryService(
                registrations,
                columnMetadata,
                browse,
                officerScope,
                scopeCatalog,
                tableScopeRegistrationService,
                authenticatedUserService,
                auditService);
        registered = new RegisteredTable(1L, CATALOG, SCHEMA, TABLE, "GOLD");
    }

    @Test
    void listTablesDropsInvisibleTable() {
        when(registrations.findByCatalogNameAndSchemaNameOrderByTableName(CATALOG, SCHEMA))
                .thenReturn(List.of(registered));
        stubScopedOfficerGeoOnly();
        stubMetadata(registered, false, List.of(), Set.of());

        assertTrue(service.listTables(CATALOG, SCHEMA).isEmpty());
    }

    @Test
    void listCatalogsDropsCatalogWithOnlyInvisibleTables() {
        when(registrations.findAllByOrderByCatalogNameAscSchemaNameAscTableNameAsc())
                .thenReturn(List.of(registered));
        stubScopedOfficerGeoOnly();
        stubMetadata(registered, false, List.of(), Set.of());

        assertTrue(service.listCatalogs().isEmpty());
    }

    @Test
    void validateColumnRefusesInvisibleTable() {
        when(registrations.findByCatalogNameAndSchemaNameAndTableName(CATALOG, SCHEMA, TABLE))
                .thenReturn(Optional.of(registered));
        stubScopedOfficerGeoOnly();
        stubMetadata(registered, false, List.of(), Set.of());

        assertThrows(IllegalArgumentException.class,
                () -> service.validateColumn(new QualifiedColumn(CATALOG, SCHEMA, TABLE, "district_code")));
    }

    @Test
    void sharedReferenceVisibleToScopedOfficer() {
        registered.setSharedReference(true);
        when(registrations.findByCatalogNameAndSchemaNameOrderByTableName(CATALOG, SCHEMA))
                .thenReturn(List.of(registered));
        stubScopedOfficerGeoOnly();
        stubMetadata(registered, true, List.of(), Set.of());

        assertEquals(1, service.listTables(CATALOG, SCHEMA).size());
    }

    @Test
    void listLayersDropWhenNoVisibleTables() {
        when(registrations.findAllByOrderByCatalogNameAscSchemaNameAscTableNameAsc())
                .thenReturn(List.of(registered));
        stubScopedOfficerGeoOnly();
        stubMetadata(registered, false, List.of(), Set.of());

        assertTrue(service.listLayers().isEmpty());
    }

    @Test
    void listSchemasDropWhenNoVisibleTableInCatalog() {
        when(registrations.findAllByOrderByCatalogNameAscSchemaNameAscTableNameAsc())
                .thenReturn(List.of(registered));
        stubScopedOfficerGeoOnly();
        stubMetadata(registered, false, List.of(), Set.of());

        assertTrue(service.listSchemas(CATALOG).isEmpty());
    }

    @Test
    void hasColumnsRefusesInvisibleTable() {
        when(registrations.findByCatalogNameAndSchemaNameAndTableName(CATALOG, SCHEMA, TABLE))
                .thenReturn(Optional.of(registered));
        stubScopedOfficerGeoOnly();
        stubMetadata(registered, false, List.of(), Set.of());

        assertThrows(IllegalArgumentException.class,
                () -> service.hasColumns(new QualifiedTable(CATALOG, SCHEMA, TABLE), List.of("district_code")));
    }

    @Test
    void superAdminBypassSeesUnboundTable() {
        when(registrations.findByCatalogNameAndSchemaNameOrderByTableName(CATALOG, SCHEMA))
                .thenReturn(List.of(registered));
        when(officerScope.currentOfficerScope()).thenReturn(TableScopePolicy.OfficerScopeView.bypass());
        lenient().when(scopeCatalog.metadataForTables(anyList()))
                .thenReturn(Map.of(1L, new TableScopePolicy.TableScopeMetadata(false, List.of(), Set.of())));

        assertEquals(1, service.listTables(CATALOG, SCHEMA).size());
    }

    private void stubScopedOfficerGeoOnly() {
        when(officerScope.currentOfficerScope())
                .thenReturn(new TableScopePolicy.OfficerScopeView(
                        false, Map.of(1L, List.of(new TableScopePolicy.AssignmentNode(2)))));
    }

    private void stubMetadata(
            RegisteredTable table,
            boolean shared,
            List<TableScopePolicy.LevelBinding> bindings,
            Set<Long> exempt) {
        var meta = new TableScopePolicy.TableScopeMetadata(shared, bindings, exempt);
        lenient().when(scopeCatalog.metadataForTables(anyList())).thenReturn(Map.of(table.getId(), meta));
        lenient().when(scopeCatalog.metadataFor(table)).thenReturn(meta);
    }
}
