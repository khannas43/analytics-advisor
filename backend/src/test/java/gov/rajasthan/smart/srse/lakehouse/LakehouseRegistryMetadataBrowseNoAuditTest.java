package gov.rajasthan.smart.srse.lakehouse;

import gov.rajasthan.smart.srse.audit.AuditService;
import gov.rajasthan.smart.srse.identity.AuthenticatedUserService;
import gov.rajasthan.smart.srse.metadata.AnalysisColumnMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Metadata cascade must not write audit rows (§7.3 A7). */
@ExtendWith(MockitoExtension.class)
class LakehouseRegistryMetadataBrowseNoAuditTest {

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

    private LakehouseRegistryService service;

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
        lenient().when(officerScope.currentOfficerScope())
                .thenReturn(TableScopePolicy.OfficerScopeView.bypass());
        lenient().when(registrations.findAllByOrderByCatalogNameAscSchemaNameAscTableNameAsc())
                .thenReturn(List.of(new RegisteredTable(1L, "c", "s", "t", "GOLD")));
        lenient().when(scopeCatalog.metadataForTables(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(java.util.Map.of(
                        1L,
                        new TableScopePolicy.TableScopeMetadata(false, List.of(), java.util.Set.of())));
    }

    @Test
    void officerCascadeDoesNotAudit() {
        service.listCatalogs();
        service.listCatalogs("GOLD");
        service.listSchemas("c");
        service.listTables("c", "s");
        service.listLayers();
        service.listSourceSystems();
        service.listTableGroups(null);
        service.listOverviewTables(null, null);

        verify(auditService, never()).recordRegistryEvent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
