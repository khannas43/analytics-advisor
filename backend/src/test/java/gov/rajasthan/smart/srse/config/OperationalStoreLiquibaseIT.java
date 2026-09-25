package gov.rajasthan.smart.srse.config;

import gov.rajasthan.smart.srse.identity.AppRoleRepository;
import gov.rajasthan.smart.srse.identity.AppUserRepository;
import gov.rajasthan.smart.srse.compiler.CompareAs;
import gov.rajasthan.smart.srse.lakehouse.QualifiedColumn;
import gov.rajasthan.smart.srse.lakehouse.RegisteredTable;
import gov.rajasthan.smart.srse.lakehouse.RegisteredTableRepository;
import gov.rajasthan.smart.srse.metadata.AnalysisColumnMetadata;
import gov.rajasthan.smart.srse.metadata.AnalysisColumnMetadataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves Liquibase creates a schema Hibernate can validate, and both operational
 * repositories round-trip a row — on PostgreSQL.
 *
 * <p>Not part of the default build (Testcontainers is slow to pull/start):
 * {@code SRSE_OPERATIONAL_INTEGRATION=true mvn -f backend/pom.xml test -Dtest=OperationalStoreLiquibaseIT}
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@EnabledIfEnvironmentVariable(named = "SRSE_OPERATIONAL_INTEGRATION", matches = "true")
class OperationalStoreLiquibaseIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("srse")
            .withUsername("srse")
            .withPassword("srse_local_pw");

    @DynamicPropertySource
    static void operationalProperties(DynamicPropertyRegistry registry) {
        registry.add("srse.datasource.operational.jdbc-url", POSTGRES::getJdbcUrl);
        registry.add("srse.datasource.operational.username", POSTGRES::getUsername);
        registry.add("srse.datasource.operational.password", POSTGRES::getPassword);
        registry.add("srse.datasource.operational.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("srse.datasource.operational.initialization-fail-timeout", () -> "-1");
        registry.add("srse.datasource.analytical.initialization-fail-timeout", () -> "-1");
        registry.add("srse.bootstrap.super-admin-password", () -> "IntegrationBootstrapPw1!");
        registry.add("srse.auth-mode", () -> "local");
    }

    @Autowired
    private RegisteredTableRepository registeredTableRepository;

    @Autowired
    private AnalysisColumnMetadataRepository columnMetadataRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AppRoleRepository appRoleRepository;

    @Test
    void liquibaseSchemaValidatesAndRepositoriesRoundTrip() {
        assertEquals(3, appRoleRepository.count());
        assertTrue(appUserRepository.count() >= 1L);
        RegisteredTable savedTable = registeredTableRepository.save(
                new RegisteredTable(null, "iceberg", "srse", "beneficiary", "GOLD"));
        assertNotNull(savedTable.getId());

        RegisteredTable loadedTable = registeredTableRepository.findById(savedTable.getId()).orElseThrow();
        assertEquals("iceberg", loadedTable.getCatalogName());
        assertEquals("beneficiary", loadedTable.getTableName());

        AnalysisColumnMetadata column = new AnalysisColumnMetadata(
                null,
                new QualifiedColumn("iceberg", "srse", "beneficiary", "m_id"),
                "Member ID",
                false,
                true);
        column.setCompareAs(CompareAs.NUMBER);
        AnalysisColumnMetadata savedColumn = columnMetadataRepository.save(column);
        assertNotNull(savedColumn.getId());

        AnalysisColumnMetadata loadedColumn = columnMetadataRepository.findById(savedColumn.getId()).orElseThrow();
        assertEquals("Member ID", loadedColumn.getBusinessName());
        assertTrue(loadedColumn.isVisible());
        assertEquals(CompareAs.NUMBER, loadedColumn.getCompareAs());
    }
}
