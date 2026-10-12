package gov.rajasthan.smart.srse.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.rajasthan.smart.srse.analysis.AnalysisAuditService;
import gov.rajasthan.smart.srse.analysis.AnalysisProperties;
import gov.rajasthan.smart.srse.audit.AuditOutcome;
import gov.rajasthan.smart.srse.compiler.RuleColumnResolver;
import gov.rajasthan.smart.srse.compiler.RuleCompiler;
import gov.rajasthan.smart.srse.analysis.RecordMatchService;
import gov.rajasthan.smart.srse.execution.AnalysisExecutionRoute;
import gov.rajasthan.smart.srse.execution.GuardrailProperties;
import gov.rajasthan.smart.srse.identity.AuthenticatedUserService;
import gov.rajasthan.smart.srse.lakehouse.AnalysisScopeFromService;
import gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService;
import gov.rajasthan.smart.srse.metadata.AnalysisColumnMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The NDJSON match stream reports an engine failure IN-BAND (an {@code error} line) and returns
 * normally. A monitor that only watches for exceptions would record a query that hit the timeout as
 * a SUCCESS, in the dashboard and in QUERY_FINISHED - the one outcome it exists to expose.
 */
class StreamFailureOutcomeTest {

    private JdbcTemplate jdbc;
    private RecordMatchService match;
    private ActiveQueryRegistry registry;
    private QueryExecutionMonitor monitor;
    private AnalysisAuditService audit;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        LakehouseRegistryService registryService = mock(LakehouseRegistryService.class);
        AnalysisProperties properties = new AnalysisProperties(5, 120, 4, 2, 10, 3, 50_000_000L, 10, 100, 100_000);
        match = new RecordMatchService(
                jdbc, registryService, new GuardrailProperties(1000, 30, 50),
                mock(AnalysisColumnMetadataRepository.class), properties, new ObjectMapper(),
                mock(AnalysisScopeFromService.class),
                new RuleCompiler(new RuleColumnResolver(registryService), properties));
        registry = new ActiveQueryRegistry(new MonitoringProperties(1000, 30));
        audit = mock(AnalysisAuditService.class);
        monitor = new QueryExecutionMonitor(
                registry, audit, mock(AuthenticatedUserService.class), new GuardrailProperties(1000, 60, 50));
    }

    private static RecordMatchService.MatchQuery query() {
        return new RecordMatchService.MatchQuery(
                "SELECT 1", List.of(), List.of("c"), "SELECT 1", AnalysisExecutionRoute.presto());
    }

    private static QueryExecutionTicket ticket() {
        return new QueryExecutionTicket("q-1", ActiveQueryKind.MATCH, "c.s.t", 1L, "user", "BYPASS", true);
    }

    @Test
    void aQueryThatHitsTheEngineTimeoutIsNotRecordedAsASuccess() throws Exception {
        doThrow(new QueryTimeoutException("Query exceeded the maximum execution time"))
                .when(jdbc).query(any(String.class), any(Object[].class), any(RowCallbackHandler.class));
        QueryExecutionTicket ticket = ticket();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        monitor.trackStream(ticket, match.match(query(), ticket.probe()::failed)).writeTo(out);

        // the client still gets the in-band error line, as before
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("\"type\":\"error\""));
        // and the monitor must say FAILURE
        verify(audit).recordQueryFinished(eq(ticket), eq(AuditOutcome.FAILURE), anyLong());
        assertEquals(ActiveQueryState.FAILURE.name(),
                registry.snapshot(Duration.ofHours(1)).recent().get(0).state());
    }
}
