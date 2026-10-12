package gov.rajasthan.smart.srse.monitoring;

import gov.rajasthan.smart.srse.analysis.AnalysisAuditService;
import gov.rajasthan.smart.srse.execution.GuardrailProperties;
import gov.rajasthan.smart.srse.identity.AuthenticatedUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryExecutionMonitorTest {

    @Mock
    private AnalysisAuditService analysisAudit;
    @Mock
    private AuthenticatedUserService users;

    private ActiveQueryRegistry registry;
    private QueryExecutionMonitor monitor;

    @BeforeEach
    void setUp() {
        registry = new ActiveQueryRegistry(new MonitoringProperties(1000, 30));
        monitor = new QueryExecutionMonitor(registry, analysisAudit, users, new GuardrailProperties(1000, 60, 50));
    }

    @Test
    void streamRemovedWhenWriterThrowsIOException() throws Exception {
        QueryExecutionTicket ticket = ticket(true);
        StreamingResponseBody inner = out -> {
            throw new IOException("client gone");
        };
        StreamingResponseBody wrapped = monitor.trackStream(ticket, inner);
        assertThrows(IOException.class, () -> wrapped.writeTo(new ByteArrayOutputStream()));
        assertTrue(registry.snapshot(java.time.Duration.ofHours(1)).running().isEmpty());
        verify(analysisAudit).recordQueryFinished(eq(ticket), eq(gov.rajasthan.smart.srse.audit.AuditOutcome.CANCELLED), anyLong());
    }

    @Test
    void failingAuditWriteDoesNotFailQuery() throws Exception {
        QueryExecutionTicket ticket = ticket(true);
        doThrow(new RuntimeException("db down")).when(analysisAudit).recordQueryFinished(any(), any(), anyLong());
        AtomicBoolean ran = new AtomicBoolean();
        monitor.track(ticket, () -> {
            ran.set(true);
            return "ok";
        });
        assertTrue(ran.get());
        assertTrue(registry.snapshot(java.time.Duration.ofHours(1)).running().isEmpty());
    }

    @Test
    void slowQueryVisibleUntilFinished() throws Exception {
        QueryExecutionTicket ticket = ticket(true);
        monitor.track(ticket, () -> {
            assertEquals(1, registry.snapshot(java.time.Duration.ofHours(1)).running().size());
            return null;
        });
        assertTrue(registry.snapshot(java.time.Duration.ofHours(1)).running().isEmpty());
    }

    private static QueryExecutionTicket ticket(boolean auditFinish) {
        return new QueryExecutionTicket(
                "id-1", ActiveQueryKind.MATCH, "c.s.t", 1L, "user", "BYPASS", auditFinish);
    }
}
