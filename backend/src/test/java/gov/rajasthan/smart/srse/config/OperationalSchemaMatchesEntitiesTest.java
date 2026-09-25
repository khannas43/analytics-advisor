package gov.rajasthan.smart.srse.config;

import java.sql.DriverManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Boots the real application context against the local operational database, so a
 * changelog that disagrees with an entity fails here instead of at deploy time.
 *
 * <p><b>Why this exists.</b> AA-18 shipped a {@code saved_query.payload_text} column
 * created as {@code clob} (PostgreSQL {@code text}) while the entity was annotated
 * {@code @Lob}, which makes Hibernate expect {@code oid}. {@code ddl-auto: validate}
 * refused to start the application — correctly — but all 510 tests passed, because
 * every slice mocks its repositories and nothing in the build ever started the app.
 * The defect was found by hand, and the next one would not have been.
 *
 * <p><b>Why not Testcontainers.</b> {@code OperationalStoreLiquibaseIT} does this
 * against a throwaway container, but it never runs: Surefire's default includes are
 * {@code *Test} / {@code Test*} / {@code *Tests} / {@code *TestCase}, and nothing
 * named {@code *IT} matches any of them, so the environment variable guarding it has
 * never once been consulted by an ordinary build. It also cannot reach the Docker
 * socket on this machine. Keep it for a clean-database check; this covers the case
 * that actually bites.
 *
 * <p>Validating against the <em>running</em> database is arguably the better check
 * anyway: it is the schema a deploy will meet, with migrations already applied.
 * Liquibase is a no-op against it and Hibernate's validation is read-only, so the
 * test changes nothing.
 *
 * <p>Skips when that database is not up, so the suite stays runnable offline.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIf("operationalDatabaseReachable")
class OperationalSchemaMatchesEntitiesTest {

    private static final String URL = "jdbc:postgresql://localhost:5433/srse";
    private static final String USER = "srse";
    private static final String PASSWORD = "srse_local_pw";

    static boolean operationalDatabaseReachable() {
        try (var ignored = DriverManager.getConnection(URL, USER, PASSWORD)) {
            return true;
        } catch (Exception e) {
            System.out.println("[schema check] skipped — no operational database at " + URL
                    + ". Start it with: docker compose up -d postgres");
            return false;
        }
    }

    @DynamicPropertySource
    static void operationalProperties(DynamicPropertyRegistry registry) {
        registry.add("srse.datasource.operational.jdbc-url", () -> URL);
        registry.add("srse.datasource.operational.username", () -> USER);
        registry.add("srse.datasource.operational.password", () -> PASSWORD);
        registry.add("srse.datasource.operational.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("srse.datasource.operational.initialization-fail-timeout", () -> "-1");
        registry.add("srse.datasource.analytical.initialization-fail-timeout", () -> "-1");
        registry.add("srse.auth-mode", () -> "mock");
    }

    @Test
    void everyEntityMatchesTheSchemaLiquibaseCreated() {
        // Reaching this line is the assertion. Spring builds the SessionFactory during
        // context startup with ddl-auto=validate, so any column whose type, name or
        // nullability has drifted from its entity fails the context before the body runs.
    }
}
