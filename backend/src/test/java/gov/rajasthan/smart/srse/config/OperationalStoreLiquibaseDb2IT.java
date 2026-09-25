package gov.rajasthan.smart.srse.config;

import gov.rajasthan.smart.srse.lakehouse.RegisteredTableRepository;
import gov.rajasthan.smart.srse.metadata.AnalysisColumnMetadataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Manual DB2 parity check — same boot path as production (Liquibase + validate)
 * against an existing DB2 volume that already has the operational tables.
 *
 * <p>Off by default, and it requires an EXPLICIT database URL — there is no
 * default, deliberately.
 *
 * <p><b>Why there is no default.</b> This test applies the changelog to whatever
 * it is pointed at, and changeset 002 RENAMES columns. It used to default to
 * {@code jdbc:db2://localhost:50000/SRSEDB}, which is SRSE's database on a
 * developer machine running both products. Running it once renamed
 * {@code BUSINESSNAME} to {@code BUSINESS_NAME} there, and every SRSE query
 * against {@code analysis_column_metadata} then failed with SQLCODE=-206 —
 * the same silent-schema-drift failure {@code docs/migrations/001} was written
 * about, this time inflicted on the other product. Give this test its own
 * database; never a shared one.
 *
 * <p>{@code SRSE_OPERATIONAL_DB2_INTEGRATION=true \
 * SRSE_OPERATIONAL_JDBC_URL=jdbc:db2://localhost:50000/AADB \
 * mvn -f backend/pom.xml test -Dtest=OperationalStoreLiquibaseDb2IT}
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "SRSE_OPERATIONAL_DB2_INTEGRATION", matches = "true")
class OperationalStoreLiquibaseDb2IT {

    /** No default: pointing this at a database by accident rewrites its schema. */
    private static final String JDBC_URL = System.getenv("SRSE_OPERATIONAL_JDBC_URL");

    /**
     * Databases this test must never touch, whatever the environment says.
     * SRSEDB belongs to SRSE, which shares the local DB2 container and does not
     * use Liquibase — applying this changelog to it renames columns out from
     * under a running application.
     */
    private static final java.util.Set<String> FORBIDDEN_DATABASES = java.util.Set.of("SRSEDB");

    @DynamicPropertySource
    static void db2Operational(DynamicPropertyRegistry registry) {
        refuseSharedDatabase(JDBC_URL);
        registry.add("srse.datasource.operational.jdbc-url", () -> JDBC_URL);
        registry.add("srse.datasource.operational.username", () -> env("SRSE_OPERATIONAL_USER", "SRSE_DB2_USER", "db2inst1"));
        registry.add("srse.datasource.operational.password", () -> env("SRSE_OPERATIONAL_PASSWORD", "SRSE_DB2_PASSWORD", "srse_local_pw"));
        registry.add("srse.datasource.operational.driver-class-name", () -> "com.ibm.db2.jcc.DB2Driver");
        registry.add("srse.datasource.operational.initialization-fail-timeout", () -> "-1");
        registry.add("srse.datasource.analytical.initialization-fail-timeout", () -> "-1");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("srse.bootstrap.super-admin-password", () -> "Db2IntegrationBootstrapPw1!");
    }

    @Autowired
    private RegisteredTableRepository registeredTableRepository;

    @Autowired
    private AnalysisColumnMetadataRepository columnMetadataRepository;

    @Test
    void contextStartsAndExistingOperationalDataIsReadable() {
        assumeTrue(JDBC_URL != null && !JDBC_URL.isBlank(),
                "Set SRSE_OPERATIONAL_JDBC_URL to a database dedicated to this product.");
        assumeTrue(db2Reachable(), () -> "DB2 not reachable at " + JDBC_URL);
        assertDoesNotThrow(() -> registeredTableRepository.findAll());
        assertDoesNotThrow(() -> columnMetadataRepository.findAll());
    }

    private static boolean db2Reachable() {
        try {
            Class.forName("com.ibm.db2.jcc.DB2Driver");
            try (var conn = java.sql.DriverManager.getConnection(
                    JDBC_URL,
                    env("SRSE_OPERATIONAL_USER", "SRSE_DB2_USER", "db2inst1"),
                    env("SRSE_OPERATIONAL_PASSWORD", "SRSE_DB2_PASSWORD", "srse_local_pw"))) {
                return conn.isValid(3);
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static String env(String primary, String legacy, String fallback) {
        String v = System.getenv(primary);
        if (v != null && !v.isBlank()) {
            return v;
        }
        v = System.getenv(legacy);
        if (v != null && !v.isBlank()) {
            return v;
        }
        return fallback;
    }

    /**
     * Fails loudly rather than skipping. Being pointed at another product's
     * database is a mistake worth surfacing, not one worth passing over in
     * silence — the damage is done at context startup, before any assertion.
     */
    static void refuseSharedDatabase(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return;
        }
        int lastSlash = jdbcUrl.lastIndexOf('/');
        String database = lastSlash >= 0 ? jdbcUrl.substring(lastSlash + 1) : jdbcUrl;
        int firstColon = database.indexOf(':');
        if (firstColon >= 0) {
            database = database.substring(0, firstColon);
        }
        if (FORBIDDEN_DATABASES.contains(database.toUpperCase(java.util.Locale.ROOT))) {
            throw new IllegalStateException(
                    "Refusing to run the operational changelog against '" + database + "'. "
                            + "That database belongs to another product sharing this DB2 container, and this "
                            + "changelog renames columns. Point SRSE_OPERATIONAL_JDBC_URL at a database "
                            + "dedicated to Analytics Advisor.");
        }
    }
}
