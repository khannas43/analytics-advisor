package gov.rajasthan.smart.srse.lakehouse;

import gov.rajasthan.smart.srse.audit.AuditActionType;
import gov.rajasthan.smart.srse.audit.AuditService;
import gov.rajasthan.smart.srse.identity.AuthenticatedUserService;
import gov.rajasthan.smart.srse.identity.AppUser;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** AA-15 — display labels (§3.1.1–3.1.4). */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class LakehouseRegistryLabelsTest {

    private static final String CATALOG = "iceberg";
    private static final String SCHEMA = "srse";
    private static final String TABLE = "beneficiary";
    private static final String LABEL = "Jan Aadhaar (Txn)";

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
    private AuthenticatedUserService authenticatedUserService;
    @Mock
    private AuditService auditService;

    private LakehouseRegistryService registry;

    private RegisteredTable tagged;

    @BeforeEach
    void setUp() {
        registry = new LakehouseRegistryService(
                registrations,
                columnMetadata,
                browse,
                officerScope,
                scopeCatalog,
                tableScopeRegistrationService,
                authenticatedUserService,
                auditService);
        tagged = new RegisteredTable(1L, CATALOG, SCHEMA, TABLE, "GOLD", false, LABEL, "Demographics");
        when(officerScope.currentOfficerScope()).thenReturn(TableScopePolicy.OfficerScopeView.bypass());
        when(scopeCatalog.metadataForTables(any())).thenReturn(Map.of(
                1L, new TableScopePolicy.TableScopeMetadata(false, List.of(), Set.of())));
        when(scopeCatalog.metadataFor(tagged)).thenReturn(
                new TableScopePolicy.TableScopeMetadata(false, List.of(), Set.of()));
    }

    @Test
    void invisibleTableAbsentFromOverviewAndSourceSystemList() {
        when(registrations.findAllByOrderByCatalogNameAscSchemaNameAscTableNameAsc())
                .thenReturn(List.of(tagged));
        when(officerScope.currentOfficerScope()).thenReturn(
                new TableScopePolicy.OfficerScopeView(
                        false, Map.of(1L, List.of(new TableScopePolicy.AssignmentNode(2)))));
        when(scopeCatalog.metadataForTables(any())).thenReturn(Map.of(
                1L, new TableScopePolicy.TableScopeMetadata(false, List.of(), Set.of())));

        assertTrue(registry.listOverviewTables(null, null).isEmpty());
        assertTrue(registry.listSourceSystems().isEmpty());
    }

    @Test
    void labelIsNotPartOfLakehouseAddress() {
        assertEquals("iceberg.srse.beneficiary", tagged.toQualifiedTable().qualifiedName());
        assertFalse(tagged.toQualifiedTable().qualifiedName().contains(LABEL));
    }

    @Test
    void vocabularyListsDistinctLabelsAndUntagged() {
        RegisteredTable untagged = new RegisteredTable(2L, CATALOG, SCHEMA, "other", "GOLD", false, null, "X");
        when(registrations.findAllByOrderByCatalogNameAscSchemaNameAscTableNameAsc())
                .thenReturn(List.of(tagged, untagged));
        assertTrue(registry.listSourceSystems().contains(LABEL));
        assertTrue(registry.listSourceSystems().contains(RegistryDisplayTags.UNTAGGED));
        assertTrue(registry.listAllSourceSystemLabels().contains(LABEL));
    }

    @Test
    void bulkRenameSourceSystemAuditedOnce() {
        AppUser admin = mock(AppUser.class);
        when(authenticatedUserService.requireCurrentUser()).thenReturn(admin);
        when(registrations.bulkRenameSourceSystem("Old Name", LABEL)).thenReturn(3);

        int n = registry.renameSourceSystemLabel("Old Name", LABEL);
        assertEquals(3, n);
        verify(auditService).recordRegistryEvent(
                eq(AuditActionType.REGISTRY_LABEL_RENAMED),
                eq(admin),
                eq("sourceSystem"),
                eq("Old Name → " + LABEL + " (3 tables)"));
    }

    @Test
    void reservedUntaggedRejectedForStorage() {
        assertThrows(IllegalArgumentException.class,
                () -> RegistryDisplayTags.normaliseOptional(RegistryDisplayTags.UNTAGGED));
    }

    @Test
    void overviewBrowsingDoesNotAudit() {
        when(registrations.findAllByOrderByCatalogNameAscSchemaNameAscTableNameAsc())
                .thenReturn(List.of(tagged));
        registry.listSourceSystems();
        registry.listTableGroups(null);
        registry.listOverviewTables(LABEL, null);
        verify(auditService, never()).recordRegistryEvent(any(), any(), any(), any());
    }
}
