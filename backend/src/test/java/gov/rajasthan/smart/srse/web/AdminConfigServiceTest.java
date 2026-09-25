package gov.rajasthan.smart.srse.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import gov.rajasthan.smart.srse.compiler.CompareAs;
import gov.rajasthan.smart.srse.config.AnalyticalConnectionService;
import gov.rajasthan.smart.srse.config.ConnectionOverrideStore;
import gov.rajasthan.smart.srse.config.OperationalConnectionService;
import gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService;
import gov.rajasthan.smart.srse.lakehouse.RegisteredTable;
import gov.rajasthan.smart.srse.lakehouse.RegisteredTableRepository;
import gov.rajasthan.smart.srse.metadata.AnalysisColumnMetadata;
import gov.rajasthan.smart.srse.metadata.AnalysisColumnMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminConfigServiceTest {

    private static final String ANALYTICAL_URL = "jdbc:presto://presto:8080";
    private static final String ANALYTICAL_USER = "srse";
    private static final String ANALYTICAL_DRIVER = "com.facebook.presto.jdbc.PrestoDriver";

    @Mock
    private HikariDataSource operational;

    @Mock
    private ConnectionOverrideStore overrideStore;

    @Mock
    private AnalyticalConnectionService analyticalConnectionService;

    @Mock
    private OperationalConnectionService operationalConnectionService;

    @Mock
    private RegisteredTableRepository registeredTableRepository;

    @Mock
    private LakehouseRegistryService registryService;

    @Mock
    private AnalysisColumnMetadataRepository columnMetadataRepository;

    private AdminConfigService service;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        service = new AdminConfigService(
                operational,
                overrideStore,
                analyticalConnectionService,
                operationalConnectionService,
                registeredTableRepository,
                registryService,
                columnMetadataRepository,
                "synthetic",
                ANALYTICAL_URL,
                ANALYTICAL_USER,
                ANALYTICAL_DRIVER);
        when(operational.getJdbcUrl()).thenReturn("jdbc:db2://db2:50000/SRSEDB");
        when(operational.getUsername()).thenReturn("db2inst1");
        when(operational.getDriverClassName()).thenReturn("com.ibm.db2.jcc.DB2Driver");
    }

    @Test
    void roundTripImportMergesWithoutRemovingExisting() {
        when(registeredTableRepository.findAll()).thenReturn(List.of(
                new RegisteredTable(1L, "iceberg", "srse", "beneficiary", "GOLD")));
        when(columnMetadataRepository.findAllByOrderByCatalogNameAscSchemaNameAscTableNameAscColumnNameAsc())
                .thenReturn(List.of(columnEntity("beneficiary", "m_id", "Member id", false, true, CompareAs.AUTO)));

        AdminConfigBundle exported = service.export();

        when(columnMetadataRepository.findByCatalogNameAndSchemaNameAndTableNameAndColumnName(
                anyString(), anyString(), anyString(), eq("m_id")))
                .thenReturn(Optional.empty());
        when(columnMetadataRepository.save(any(AnalysisColumnMetadata.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AdminConfigService.ImportResult result = service.importConfig(
                exported, new AdminConfigService.ImportOptions(false));

        verify(registryService).importRegistration("iceberg", "srse", "beneficiary", "GOLD");
        verify(registeredTableRepository, never()).deleteAll();
        assertEquals(1, result.registeredTableCount());
        assertEquals(1, result.columnMetadataCount());
        assertTrue(result.importedTables().contains("iceberg.srse.beneficiary"));
    }

    @Test
    void exportOmitsOtpGatewaySecretsEvenWhenPresentInOverrideFile() throws Exception {
        Properties overrides = new Properties();
        overrides.setProperty("srse.otp.smtp.host", "smtp.internal");
        overrides.setProperty("srse.otp.smtp.password", "smtp-secret");
        overrides.setProperty("srse.otp.sms.endpoint", "https://sms.example/send");
        overrides.setProperty("srse.otp.sms.password", "sms-secret");
        overrides.setProperty("operational.jdbc-url", "jdbc:db2://db2:50000/SRSEDB");
        overrides.setProperty("operational.username", "db2inst1");
        overrides.setProperty("operational.driver-class-name", "com.ibm.db2.jcc.DB2Driver");
        overrides.setProperty("analytical.jdbc-url", ANALYTICAL_URL);
        overrides.setProperty("analytical.username", ANALYTICAL_USER);
        overrides.setProperty("analytical.driver-class-name", ANALYTICAL_DRIVER);
        when(overrideStore.load()).thenReturn(Optional.of(overrides));
        when(registeredTableRepository.findAll()).thenReturn(List.of());
        when(columnMetadataRepository.findAllByOrderByCatalogNameAscSchemaNameAscTableNameAscColumnNameAsc())
                .thenReturn(List.of());

        AdminConfigBundle bundle = service.export();
        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(bundle));

        assertFalse(root.has("srse"));
        assertFalse(root.toString().contains("smtp-secret"));
        assertFalse(root.toString().contains("sms-secret"));
        assertFalse(root.toString().contains("smtp.internal"));
    }

    @Test
    void exportMasksPasswordsInSerialisedJson() throws Exception {
        Properties overrides = new Properties();
        overrides.setProperty("operational.password", "db2-secret");
        overrides.setProperty("operational.jdbc-url", "jdbc:db2://db2:50000/SRSEDB");
        overrides.setProperty("operational.username", "db2inst1");
        overrides.setProperty("operational.driver-class-name", "com.ibm.db2.jcc.DB2Driver");
        overrides.setProperty("analytical.password", "presto-secret");
        overrides.setProperty("analytical.jdbc-url", ANALYTICAL_URL);
        overrides.setProperty("analytical.username", ANALYTICAL_USER);
        overrides.setProperty("analytical.driver-class-name", ANALYTICAL_DRIVER);
        when(overrideStore.load()).thenReturn(Optional.of(overrides));
        when(registeredTableRepository.findAll()).thenReturn(List.of());
        when(columnMetadataRepository.findAllByOrderByCatalogNameAscSchemaNameAscTableNameAscColumnNameAsc())
                .thenReturn(List.of());

        AdminConfigBundle bundle = service.export();
        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(bundle));

        assertNull(root.path("connections").path("operational").path("password").textValue());
        assertNull(root.path("connections").path("analytical").path("password").textValue());
        assertFalse(root.path("connections").path("operational").path("password").isTextual());
    }

    @Test
    void importPreservesExistingPasswordWhenFileHasNull() {
        Properties existing = new Properties();
        existing.setProperty("operational.password", "keep-me");
        when(overrideStore.load()).thenReturn(Optional.of(existing));

        AdminConfigBundle bundle = new AdminConfigBundle(
                "2.0",
                Instant.now(),
                "synthetic",
                new AdminConfigBundle.ConnectionBundle(
                        new AdminConfigBundle.ConnectionPlane(
                                "jdbc:db2://db2:50000/SRSEDB", "db2inst1", null, "com.ibm.db2.jcc.DB2Driver"),
                        null),
                List.of(),
                List.of());

        service.importConfig(bundle, new AdminConfigService.ImportOptions(false));

        ArgumentCaptor<Properties> captor = ArgumentCaptor.forClass(Properties.class);
        verify(overrideStore).save(captor.capture());
        assertEquals("keep-me", captor.getValue().getProperty("operational.password"));
        verify(operationalConnectionService, never()).update(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void legacySchemaImportsSurvivorsAndReportsSkippedLegacySections() {
        AdminConfigBundle legacy = new AdminConfigBundle(
                "1.0",
                Instant.now(),
                "live",
                null,
                List.of(new AdminConfigBundle.RegisteredTableEntry("iceberg", "srse", "beneficiary", "GOLD")),
                List.of());

        AdminConfigService.ImportResult result = service.importConfig(
                legacy, new AdminConfigService.ImportOptions(false));

        verify(registryService).importRegistration("iceberg", "srse", "beneficiary", "GOLD");
        assertEquals(1, result.registeredTableCount());
        List<String> skippedSections = result.skipped().stream()
                .map(AdminConfigService.SkippedSection::section)
                .toList();
        assertTrue(skippedSections.contains("fieldCatalog"));
        assertTrue(skippedSections.contains("fieldColumnMappings"));
        assertTrue(skippedSections.contains("schemes"));
    }

    @Test
    void absentRegisteredTablesSectionIsReportedAndExistingUntouched() {
        when(registeredTableRepository.findAll()).thenReturn(List.of(
                new RegisteredTable(1L, "iceberg", "srse", "stay", "GOLD")));

        AdminConfigBundle bundle = new AdminConfigBundle(
                "2.0",
                Instant.now(),
                "synthetic",
                null,
                null,
                List.of(new AdminConfigBundle.AnalysisColumnMetadataEntry(
                        "iceberg", "srse", "stay", "col", "Label", false, true, CompareAs.AUTO)));

        when(columnMetadataRepository.findByCatalogNameAndSchemaNameAndTableNameAndColumnName(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(columnMetadataRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminConfigService.ImportResult result = service.importConfig(
                bundle, new AdminConfigService.ImportOptions(false));

        verify(registryService, never()).importRegistration(anyString(), anyString(), anyString(), anyString());
        assertTrue(result.skipped().stream()
                .anyMatch(s -> s.section().equals("registeredTables")));
        verify(registeredTableRepository, never()).deleteAll();
    }

    private static AnalysisColumnMetadata columnEntity(
            String table, String column, String businessName,
            boolean fuzzy, boolean visible, CompareAs compareAs) {
        AnalysisColumnMetadata entity = new AnalysisColumnMetadata(
                1L,
                new gov.rajasthan.smart.srse.lakehouse.QualifiedColumn(
                        "iceberg", "srse", table, column),
                businessName,
                fuzzy,
                visible);
        entity.setCompareAs(compareAs);
        return entity;
    }
}
