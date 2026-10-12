package gov.rajasthan.smart.srse.metadata;

import gov.rajasthan.smart.srse.analysis.AnalysisProperties;
import gov.rajasthan.smart.srse.analysis.ColumnDistinctValuesService;
import gov.rajasthan.smart.srse.audit.AuditService;
import gov.rajasthan.smart.srse.identity.AuthenticatedUserService;
import gov.rajasthan.smart.srse.lakehouse.AnalysisScopeFromService;
import gov.rajasthan.smart.srse.lakehouse.LakehouseBrowseService;
import gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService;
import gov.rajasthan.smart.srse.lakehouse.OfficerRegistryScopeService;
import gov.rajasthan.smart.srse.lakehouse.QualifiedColumn;
import gov.rajasthan.smart.srse.lakehouse.RegisteredTableRepository;
import gov.rajasthan.smart.srse.lakehouse.RegisteredTableScopeCatalog;
import gov.rajasthan.smart.srse.lakehouse.TableScopeRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Aa1 / decision 0.11 — a distinct-values list is opt-in per column, enforced on the server. */
class ValuePickerFlagTest {

    private static final QualifiedColumn COLUMN =
            new QualifiedColumn("iceberg", "srse", "beneficiary", "district");

    private AnalysisColumnMetadataRepository metadata;
    private LakehouseRegistryService registry;

    @BeforeEach
    void setUp() {
        metadata = mock(AnalysisColumnMetadataRepository.class);
        registry = new LakehouseRegistryService(
                mock(RegisteredTableRepository.class), metadata, mock(LakehouseBrowseService.class),
                mock(OfficerRegistryScopeService.class), mock(RegisteredTableScopeCatalog.class),
                mock(TableScopeRegistrationService.class), mock(AuthenticatedUserService.class),
                mock(AuditService.class));
    }

    private void stored(boolean enabled) {
        AnalysisColumnMetadata row = new AnalysisColumnMetadata(1L, COLUMN, null, false, true);
        row.setValuePickerEnabled(enabled);
        when(metadata.findByCatalogNameAndSchemaNameAndTableNameAndColumnName(
                "iceberg", "srse", "beneficiary", "district")).thenReturn(Optional.of(row));
    }

    @Test
    void aColumnWithNoMetadataRowHasNoPicker() {
        when(metadata.findByCatalogNameAndSchemaNameAndTableNameAndColumnName(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> registry.requireValuePickerEnabled(COLUMN));
        assertTrue(failure.getMessage().contains("Type the value instead"), failure.getMessage());
    }

    @Test
    void aColumnWithThePickerSwitchedOffIsRefused() {
        stored(false);
        assertThrows(IllegalArgumentException.class, () -> registry.requireValuePickerEnabled(COLUMN));
    }

    @Test
    void aColumnWithThePickerSwitchedOnPasses() {
        stored(true);
        assertDoesNotThrow(() -> registry.requireValuePickerEnabled(COLUMN));
    }

    @Test
    void newMetadataRowsDefaultToNoPicker() {
        assertFalse(new AnalysisColumnMetadata(null, COLUMN, "District", true, true).isValuePickerEnabled());
    }

    @Test
    void theColumnValuesEndpointRefusesBeforeAnySqlIsPlannedOrRun() {
        LakehouseRegistryService gate = mock(LakehouseRegistryService.class);
        doThrow(new IllegalArgumentException("A value list is not available"))
                .when(gate).requireValuePickerEnabled(any());
        AnalysisScopeFromService scope = mock(AnalysisScopeFromService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ColumnDistinctValuesService service = new ColumnDistinctValuesService(
                jdbc, gate, scope, new AnalysisProperties(5, 120, 4, 2, 10, 3, 50_000_000L, 10, 100, 100_000));

        assertThrows(IllegalArgumentException.class, () -> service.plan(
                new ColumnDistinctValuesService.ColumnValuesRequest(
                        "iceberg", "srse", "beneficiary", "district", null)));

        verify(scope, never()).planFrom(any());
    }

    @Test
    void anAdminSaveThatOmitsTheFlagKeepsWhatIsStored() {
        AnalysisColumnMetadata existing = new AnalysisColumnMetadata(7L, COLUMN, "District", false, true);
        existing.setValuePickerEnabled(true);
        AnalysisColumnMetadataRepository repo = mock(AnalysisColumnMetadataRepository.class);
        when(repo.findByCatalogNameAndSchemaNameAndTableNameAndColumnName(
                "iceberg", "srse", "beneficiary", "district")).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        AnalysisColumnMetadataController controller = new AnalysisColumnMetadataController(
                repo, mock(LakehouseRegistryService.class), mock(LakehouseBrowseService.class));

        // an older client: no valuePickerEnabled in the body
        var response = controller.upsert(new AnalysisColumnMetadataController.UpsertColumnMetadataRequest(
                "iceberg", "srse", "beneficiary", "district", "District renamed", false, true, null));

        assertTrue(response.valuePickerEnabled());
        assertEquals("District renamed", response.businessName());

        var cleared = controller.upsert(new AnalysisColumnMetadataController.UpsertColumnMetadataRequest(
                "iceberg", "srse", "beneficiary", "district", "District", false, true, null, false));
        assertFalse(cleared.valuePickerEnabled());
    }
}
