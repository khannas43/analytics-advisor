package gov.rajasthan.smart.srse.execution;

import gov.rajasthan.smart.srse.datasource.ExternalDataSource;
import gov.rajasthan.smart.srse.datasource.ExternalDataSourceService;
import gov.rajasthan.smart.srse.datasource.ExternalDataSourceType;
import gov.rajasthan.smart.srse.lakehouse.QualifiedTable;
import gov.rajasthan.smart.srse.lakehouse.RegisteredTable;
import gov.rajasthan.smart.srse.lakehouse.RegisteredTableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisExecutionRouterTest {

    private final RegisteredTableRepository registrations = mock(RegisteredTableRepository.class);
    private final ExternalDataSourceService sources = mock(ExternalDataSourceService.class);
    private final JdbcTemplate presto = mock(JdbcTemplate.class);
    private AnalysisExecutionRouter router;

    @BeforeEach
    void setUp() {
        router = new AnalysisExecutionRouter(registrations, sources, presto);
    }

    @Test
    void sameExternalSourceUsesDirectJdbcAndPhysicalTable() {
        QualifiedTable logical = new QualifiedTable("jdbc_7", "public", "people");
        RegisteredTable registration = registration(7L, "public", "people", "egov_db");
        ExternalDataSource source = source(ExternalDataSourceType.POSTGRESQL, null, "DG2.0");
        when(registrations.findByCatalogNameAndSchemaNameAndTableName("jdbc_7", "public", "people"))
                .thenReturn(Optional.of(registration));
        when(sources.requireActiveInternal(7L)).thenReturn(source);

        AnalysisExecutionRouter.RoutedSql routed = router.route(
                "SELECT src.id AS \"source_id\" FROM jdbc_7.public.people src",
                List.of(logical));

        assertEquals(AnalysisExecutionRoute.Mode.DIRECT_JDBC, routed.route().mode());
        assertEquals("SELECT src.id AS \"source_id\" FROM \"public\".\"people\" src", routed.sql());
    }

    @Test
    void crossSourceRequiresFederationCatalog() {
        QualifiedTable nativeTable = new QualifiedTable("iceberg", "gold", "people");
        QualifiedTable externalTable = new QualifiedTable("jdbc_7", "public", "people");
        RegisteredTable nativeRegistration = mock(RegisteredTable.class);
        RegisteredTable externalRegistration = registration(7L, "public", "people", "egov_db");
        when(nativeRegistration.isExternal()).thenReturn(false);
        when(registrations.findByCatalogNameAndSchemaNameAndTableName("iceberg", "gold", "people"))
                .thenReturn(Optional.of(nativeRegistration));
        when(registrations.findByCatalogNameAndSchemaNameAndTableName("jdbc_7", "public", "people"))
                .thenReturn(Optional.of(externalRegistration));
        ExternalDataSource source = source(ExternalDataSourceType.POSTGRESQL, null, "DG2.0");
        when(sources.requireActiveInternal(7L)).thenReturn(source);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> router.route(
                "SELECT * FROM iceberg.gold.people a JOIN jdbc_7.public.people b ON a.id=b.id",
                List.of(nativeTable, externalTable)));
        assertTrue(failure.getMessage().contains("Cross-source"));
        assertTrue(failure.getMessage().contains("DG2.0"));
    }

    @Test
    void longerTableNameIsNotRewrittenAsPrefix() {
        QualifiedTable people = new QualifiedTable("jdbc_7", "public", "people");
        QualifiedTable peopleExtra = new QualifiedTable("jdbc_7", "public", "people_extra");
        when(registrations.findByCatalogNameAndSchemaNameAndTableName("jdbc_7", "public", "people"))
                .thenReturn(Optional.of(registration(7L, "public", "people", "egov_db")));
        when(registrations.findByCatalogNameAndSchemaNameAndTableName("jdbc_7", "public", "people_extra"))
                .thenReturn(Optional.of(registration(7L, "public", "people_extra", "egov_db")));
        when(sources.requireActiveInternal(7L)).thenReturn(source(ExternalDataSourceType.POSTGRESQL, null, "Payroll"));

        AnalysisExecutionRouter.RoutedSql routed = router.route(
                "SELECT * FROM jdbc_7.public.people_extra a JOIN jdbc_7.public.people b",
                List.of(people, peopleExtra));

        assertEquals(
                "SELECT * FROM \"public\".\"people_extra\" a JOIN \"public\".\"people\" b",
                routed.sql());
        assertFalse(routed.sql().contains("Payroll"));
    }

    @Test
    void unsafeSchemaIsRejectedBeforeInterpolation() {
        QualifiedTable logical = new QualifiedTable("jdbc_7", "public", "people");
        RegisteredTable registration = registration(7L, "public\";drop", "people", "egov_db");
        when(registrations.findByCatalogNameAndSchemaNameAndTableName("jdbc_7", "public", "people"))
                .thenReturn(Optional.of(registration));
        when(sources.requireActiveInternal(7L))
                .thenReturn(source(ExternalDataSourceType.POSTGRESQL, null, "Payroll"));

        assertThrows(IllegalArgumentException.class, () -> router.route(
                "SELECT * FROM jdbc_7.public.people", List.of(logical)));
    }

    @Test
    void sameSourcePrestoFunctionAsksForAliasWithoutSayingCrossSource() {
        QualifiedTable logical = new QualifiedTable("jdbc_7", "public", "people");
        when(registrations.findByCatalogNameAndSchemaNameAndTableName("jdbc_7", "public", "people"))
                .thenReturn(Optional.of(registration(7L, "public", "people", "egov_db")));
        when(sources.requireActiveInternal(7L))
                .thenReturn(source(ExternalDataSourceType.POSTGRESQL, null, "Payroll"));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> router.route(
                "SELECT levenshtein_distance(a, b) FROM jdbc_7.public.people", List.of(logical)));
        assertTrue(failure.getMessage().contains("Presto-only"));
        assertFalse(failure.getMessage().contains("Cross-source"));
    }

    @Test
    void mysqlDirectSqlUsesBackticksCharCastAndKeepsLimit() {
        QualifiedTable logical = new QualifiedTable("jdbc_7", "app", "people");
        when(registrations.findByCatalogNameAndSchemaNameAndTableName("jdbc_7", "app", "people"))
                .thenReturn(Optional.of(registration(7L, "app", "people", "app")));
        when(sources.requireActiveInternal(7L))
                .thenReturn(source(ExternalDataSourceType.MYSQL, null, "Payroll"));

        AnalysisExecutionRouter.RoutedSql routed = router.route(
                "SELECT DISTINCT scoped.district AS \"v\" FROM jdbc_7.app.people scoped "
                        + "WHERE lower(CAST(scoped.district AS VARCHAR)) LIKE lower(?) "
                        + "ORDER BY v NULLS FIRST LIMIT 11",
                List.of(logical));

        assertEquals(AnalysisExecutionRoute.Mode.DIRECT_JDBC, routed.route().mode());
        assertTrue(routed.sql().contains("`app`.`people`"), routed.sql());
        assertTrue(routed.sql().contains("AS CHAR)"), routed.sql());
        assertTrue(routed.sql().contains("AS `v`"), routed.sql());
        assertTrue(routed.sql().contains("LIMIT 11"), routed.sql());
        assertFalse(routed.sql().contains("NULLS FIRST"), routed.sql());
        assertFalse(routed.sql().contains("jdbc_7"), routed.sql());
    }

    @Test
    void sqlServerAddsTopAndWidensVarchar() {
        QualifiedTable logical = new QualifiedTable("jdbc_7", "dbo", "people");
        when(registrations.findByCatalogNameAndSchemaNameAndTableName("jdbc_7", "dbo", "people"))
                .thenReturn(Optional.of(registration(7L, "dbo", "people", "app")));
        when(sources.requireActiveInternal(7L))
                .thenReturn(source(ExternalDataSourceType.SQLSERVER, null, "Payroll"));

        AnalysisExecutionRouter.RoutedSql routed = router.route(
                "SELECT CAST(scoped.district AS VARCHAR) AS v FROM jdbc_7.dbo.people scoped "
                        + "ORDER BY v NULLS FIRST LIMIT 11",
                List.of(logical));

        assertTrue(routed.sql().startsWith("SELECT TOP (11) "), routed.sql());
        assertTrue(routed.sql().contains("AS VARCHAR(MAX)"), routed.sql());
        assertTrue(routed.sql().contains("[dbo].[people]"), routed.sql());
        assertFalse(routed.sql().contains(" LIMIT "), routed.sql());
        assertFalse(routed.sql().contains("NULLS FIRST"), routed.sql());
    }

    @Test
    void oracleRewritesLimitAndVarchar() {
        QualifiedTable logical = new QualifiedTable("jdbc_7", "HR", "PEOPLE");
        when(registrations.findByCatalogNameAndSchemaNameAndTableName("jdbc_7", "HR", "PEOPLE"))
                .thenReturn(Optional.of(registration(7L, "HR", "PEOPLE", "APP")));
        when(sources.requireActiveInternal(7L))
                .thenReturn(source(ExternalDataSourceType.ORACLE, null, "Payroll"));

        AnalysisExecutionRouter.RoutedSql routed = router.route(
                "SELECT CAST(scoped.district AS VARCHAR) FROM jdbc_7.HR.PEOPLE scoped LIMIT 11",
                List.of(logical));

        assertTrue(routed.sql().contains("\"HR\".\"PEOPLE\""), routed.sql());
        assertTrue(routed.sql().contains("AS VARCHAR2(4000)"), routed.sql());
        assertTrue(routed.sql().contains("FETCH FIRST 11 ROWS ONLY"), routed.sql());
    }

    @Test
    void directRouteUsesSavedJdbcTemplate() {
        JdbcTemplate direct = mock(JdbcTemplate.class);
        when(sources.jdbcTemplateForExecution(7L)).thenReturn(direct);
        assertSame(direct, router.jdbc(new AnalysisExecutionRoute(AnalysisExecutionRoute.Mode.DIRECT_JDBC, 7L)));
        assertSame(presto, router.jdbc(AnalysisExecutionRoute.presto()));
        assertSame(presto, router.jdbc(new AnalysisExecutionRoute(AnalysisExecutionRoute.Mode.FEDERATED_PRESTO, null)));
    }

    @Test
    void crossSourceUsesConfiguredFederationCatalog() {
        QualifiedTable nativeTable = new QualifiedTable("iceberg", "gold", "people");
        QualifiedTable externalTable = new QualifiedTable("jdbc_7", "public", "people");
        RegisteredTable nativeRegistration = mock(RegisteredTable.class);
        RegisteredTable externalRegistration = registration(7L, "public", "people", "egov_db");
        when(nativeRegistration.isExternal()).thenReturn(false);
        when(registrations.findByCatalogNameAndSchemaNameAndTableName("iceberg", "gold", "people"))
                .thenReturn(Optional.of(nativeRegistration));
        when(registrations.findByCatalogNameAndSchemaNameAndTableName("jdbc_7", "public", "people"))
                .thenReturn(Optional.of(externalRegistration));
        ExternalDataSource source = source(ExternalDataSourceType.POSTGRESQL, "egov", "DG2.0");
        when(sources.requireActiveInternal(7L)).thenReturn(source);

        AnalysisExecutionRouter.RoutedSql routed = router.route(
                "SELECT * FROM iceberg.gold.people a JOIN jdbc_7.public.people b ON a.id=b.id",
                List.of(nativeTable, externalTable));

        assertEquals(AnalysisExecutionRoute.Mode.FEDERATED_PRESTO, routed.route().mode());
        assertEquals(
                "SELECT * FROM iceberg.gold.people a JOIN \"egov\".\"public\".\"people\" b ON a.id=b.id",
                routed.sql());
        assertFalse(routed.sql().contains("DG2.0"));
    }

    private static RegisteredTable registration(long sourceId, String schema, String table, String catalog) {
        RegisteredTable row = new RegisteredTable(sourceId, "jdbc_" + sourceId, schema, table, null, false, null, null);
        row.attachExternalSource(sourceId, catalog);
        return row;
    }

    private static ExternalDataSource source(
            ExternalDataSourceType type, String federationCatalog, String name) {
        return new ExternalDataSource(
                name, type, "jdbc:postgresql://db.internal:5432/app", "reader", "ciphertext-value", federationCatalog);
    }
}
