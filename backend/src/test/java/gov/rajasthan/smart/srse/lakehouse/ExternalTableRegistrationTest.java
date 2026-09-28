package gov.rajasthan.smart.srse.lakehouse;

import gov.rajasthan.smart.srse.audit.AuditService;
import gov.rajasthan.smart.srse.datasource.ExternalDataSource;
import gov.rajasthan.smart.srse.datasource.ExternalDataSourceMetadataService;
import gov.rajasthan.smart.srse.datasource.ExternalDataSourceService;
import gov.rajasthan.smart.srse.datasource.ExternalDataSourceType;
import gov.rajasthan.smart.srse.identity.AppUser;
import gov.rajasthan.smart.srse.identity.AuthenticatedUserService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalTableRegistrationTest {

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
    @Mock
    private ExternalDataSourceMetadataService externalMetadata;
    @Mock
    private ExternalDataSourceService externalSources;
    @Mock
    private AppUser actor;

    private LakehouseRegistryService service;

    @BeforeEach
    void setUp() {
        service = new LakehouseRegistryService(
                registrations, columnMetadata, browse, officerScope, scopeCatalog,
                tableScopeRegistrationService, authenticatedUserService, auditService,
                externalMetadata, externalSources);
    }

    @Test
    void registrationUsesLogicalCatalogAndStaysUnshared() {
        when(externalSources.requireForBrowse(7L)).thenReturn(source(ExternalDataSourceType.POSTGRESQL));
        when(registrations.findByCatalogNameAndSchemaNameAndTableName("jdbc_7", "public", "people"))
                .thenReturn(Optional.empty());
        when(registrations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authenticatedUserService.requireCurrentUser()).thenReturn(actor);

        RegisteredTable saved = service.registerExternal(7L, "egov-db", "public", "people", null, null, null);

        assertEquals("jdbc_7", saved.getCatalogName());
        assertEquals("public", saved.getSchemaName());
        assertEquals("people", saved.getTableName());
        assertEquals("egov-db", saved.getExternalCatalog());
        assertEquals(7L, saved.getExternalDataSourceId());
        assertFalse(saved.isSharedReference());
        assertFalse(saved.toQualifiedTable().qualifiedName().contains("Payroll"));
        verify(externalMetadata).validateRegisteredTableCandidate(7L, "egov-db", "public", "people");
    }

    @Test
    void mysqlSchemaLessTableUsesTheDatabaseNameAsSchema() {
        when(externalSources.requireForBrowse(7L)).thenReturn(source(ExternalDataSourceType.MYSQL));
        when(registrations.findByCatalogNameAndSchemaNameAndTableName("jdbc_7", "appdb", "people"))
                .thenReturn(Optional.empty());
        when(registrations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authenticatedUserService.requireCurrentUser()).thenReturn(actor);

        RegisteredTable saved = service.registerExternal(7L, "appdb", null, "people", null, null, null);

        assertEquals("jdbc_7.appdb.people", saved.toQualifiedTable().qualifiedName());
        verify(externalMetadata).validateRegisteredTableCandidate(7L, "appdb", null, "people");
    }

    @Test
    void unsafeTableNameIsRejected() {
        when(externalSources.requireForBrowse(7L)).thenReturn(source(ExternalDataSourceType.POSTGRESQL));

        assertThrows(IllegalArgumentException.class,
                () -> service.registerExternal(7L, "egov", "public", "people;drop", null, null, null));
        verify(registrations, never()).save(any());
    }

    @Test
    void scopedOfficerCannotSeeAnUnsharedExternalTable() {
        RegisteredTable row = externalRow();
        when(registrations.findAllByOrderByCatalogNameAscSchemaNameAscTableNameAsc()).thenReturn(List.of(row));
        when(registrations.findByCatalogNameAndSchemaNameAndTableName("jdbc_7", "public", "people"))
                .thenReturn(Optional.of(row));
        when(officerScope.currentOfficerScope()).thenReturn(TableScopePolicy.OfficerScopeView.denyScoped());
        when(scopeCatalog.metadataForTables(any())).thenReturn(Map.of());
        when(scopeCatalog.metadataFor(row)).thenReturn(
                new TableScopePolicy.TableScopeMetadata(false, List.of(), Set.of()));

        assertTrue(service.listCatalogs().isEmpty());
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.requireVisibleRegistration("jdbc_7", "public", "people"));
        assertTrue(failure.getMessage().contains("not visible"));
    }

    @Test
    void superAdminSeesTheLogicalCatalog() {
        RegisteredTable row = externalRow();
        when(registrations.findAllByOrderByCatalogNameAscSchemaNameAscTableNameAsc()).thenReturn(List.of(row));
        when(officerScope.currentOfficerScope()).thenReturn(TableScopePolicy.OfficerScopeView.bypass());
        when(scopeCatalog.metadataForTables(any())).thenReturn(Map.of());

        assertEquals(List.of("jdbc_7"), service.listCatalogs());
    }

    private static ExternalDataSource source(ExternalDataSourceType type) {
        return new ExternalDataSource(
                "Payroll", type, "jdbc:postgresql://db.internal:5432/app", "reader", "ciphertext-value", null);
    }

    private static RegisteredTable externalRow() {
        RegisteredTable row = new RegisteredTable(9L, "jdbc_7", "public", "people", null, false, "Payroll", null);
        row.attachExternalSource(7L, "egov-db");
        return row;
    }
}
