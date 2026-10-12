package gov.rajasthan.smart.srse.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real operational PostgreSQL — verifies QUERY_FINISHED rows and duration_ms persist (L2).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIf("operationalDatabaseReachable")
class QueryAuditPersistenceTest {

    private static final String URL = "jdbc:postgresql://localhost:5433/srse";
    private static final String USER = "srse";
    private static final String PASSWORD = "srse_local_pw";
    private static final String MARKER = "zz_it_query_audit";

    static boolean operationalDatabaseReachable() {
        try (var ignored = DriverManager.getConnection(URL, USER, PASSWORD)) {
            return true;
        } catch (Exception e) {
            System.out.println("[query audit persistence] skipped — no operational database at " + URL);
            return false;
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("srse.datasource.operational.jdbc-url", () -> URL);
        registry.add("srse.datasource.operational.username", () -> USER);
        registry.add("srse.datasource.operational.password", () -> PASSWORD);
        registry.add("srse.datasource.operational.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("srse.datasource.operational.initialization-fail-timeout", () -> "-1");
        registry.add("srse.datasource.analytical.initialization-fail-timeout", () -> "-1");
        registry.add("srse.auth-mode", () -> "mock");
        registry.add("srse.metadata.sync.enabled", () -> "false");
    }

    @Autowired
    private AuditCaptureService auditCapture;
    @Autowired
    private AuditEventRepository repository;

    private final List<Long> insertedIds = new java.util.ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long id : insertedIds) {
            // No delete API — test rows remain tagged by MARKER in detail for manual cleanup if needed.
        }
        insertedIds.clear();
    }

    @Test
    void queryFinishedPersistsDurationAndMatchesStartQueryId() {
        String queryId = UUID.randomUUID().toString();
        auditCapture.recordBestEffort(new AuditEventDraft(
                AuditActionType.QUERY_EXECUTED,
                AuditOutcome.SUCCESS,
                null,
                null,
                MARKER + "; queryId=" + queryId,
                "iceberg.srse.beneficiary",
                "SELECT ?",
                "BYPASS"));
        auditCapture.recordBestEffort(new AuditEventDraft(
                AuditActionType.QUERY_FINISHED,
                AuditOutcome.SUCCESS,
                null,
                null,
                MARKER + "; queryId=" + queryId,
                "iceberg.srse.beneficiary",
                null,
                "BYPASS",
                42L));

        AuditEvent finished = repository.findAll().stream()
                .filter(e -> e.getActionType() == AuditActionType.QUERY_FINISHED)
                .filter(e -> e.getDetail() != null && e.getDetail().contains(queryId))
                .filter(e -> e.getDetail().contains(MARKER))
                .reduce((first, second) -> second)
                .orElseThrow();
        insertedIds.add(finished.getId());

        AuditEvent started = repository.findAll().stream()
                .filter(e -> e.getActionType() == AuditActionType.QUERY_EXECUTED)
                .filter(e -> e.getDetail() != null && e.getDetail().contains(queryId))
                .filter(e -> e.getDetail().contains(MARKER))
                .findFirst()
                .orElseThrow();

        assertEquals(42L, finished.getDurationMs());
        assertTrue(started.getDetail().contains("queryId=" + queryId));
        assertNotNull(finished.getOccurredAt());
    }

    @Test
    void columnValuesRowCarriesDurationMs() {
        auditCapture.recordBestEffort(new AuditEventDraft(
                AuditActionType.QUERY_EXECUTED,
                AuditOutcome.SUCCESS,
                null,
                null,
                MARKER + " column-values",
                "iceberg.srse.beneficiary",
                "SELECT DISTINCT ?",
                "BYPASS",
                99L));

        AuditEvent row = repository.findAll().stream()
                .filter(e -> e.getDetail() != null && e.getDetail().contains(MARKER + " column-values"))
                .reduce((a, b) -> b)
                .orElseThrow();
        assertEquals(99L, row.getDurationMs());
    }
}
