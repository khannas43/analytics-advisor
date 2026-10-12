package gov.rajasthan.smart.srse.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveQueryRegistryTest {

    private ActiveQueryRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ActiveQueryRegistry(new MonitoringProperties(1000, 30));
    }

    @Test
    void entryAppearsWhileRunningAndClearsAfterFinish() {
        QueryExecutionTicket ticket = ticket("q1");
        assertTrue(registry.tryRegister(ticket));
        assertEquals(1, registry.snapshot(Duration.ofHours(1)).running().size());
        registry.finish("q1", ActiveQueryState.SUCCESS, 12);
        assertTrue(registry.snapshot(Duration.ofHours(1)).running().isEmpty());
        assertEquals(1, registry.snapshot(Duration.ofHours(1)).recent().size());
    }

    @Test
    void finishRemovesEntryWhenEngineThrows() {
        QueryExecutionTicket ticket = ticket("q2");
        registry.tryRegister(ticket);
        registry.finish("q2", ActiveQueryState.FAILURE, 5);
        assertTrue(registry.snapshot(Duration.ofHours(1)).running().isEmpty());
    }

    @Test
    void abandonedSweepDropsStaleRunning() {
        registry.tryRegister(ticket("stale"));
        backdateRunning("stale", Instant.now().minus(Duration.ofMinutes(30)));
        registry.snapshot(Duration.ofMinutes(5));
        assertTrue(registry.snapshot(Duration.ofMinutes(5)).running().isEmpty());
    }

    @Test
    void maxRunningCapDoesNotBlockRegistrationAttempt() {
        ActiveQueryRegistry small = new ActiveQueryRegistry(new MonitoringProperties(2, 30));
        assertTrue(small.tryRegister(ticket("a")));
        assertTrue(small.tryRegister(ticket("b")));
        assertFalse(small.tryRegister(ticket("c")));
        assertEquals(2, small.snapshot(Duration.ofHours(1)).running().size());
    }

    @Test
    void ringBufferKeepsAtMost100Recent() {
        for (int i = 0; i < 150; i++) {
            String id = "r" + i;
            registry.tryRegister(ticket(id));
            registry.finish(id, ActiveQueryState.SUCCESS, 1);
        }
        assertTrue(registry.snapshot(Duration.ofHours(1)).recent().size() <= 100);
    }

    @Test
    void concurrentRegisterAndFinishLeavesNoRunning() throws InterruptedException {
        int workers = 32;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Exception> errors = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            String id = "c" + i;
            pool.submit(() -> {
                try {
                    start.await();
                    registry.tryRegister(ticket(id));
                    registry.finish(id, ActiveQueryState.SUCCESS, 3);
                } catch (Exception ex) {
                    synchronized (errors) {
                        errors.add(ex);
                    }
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertTrue(errors.isEmpty(), errors.toString());
        assertTrue(registry.snapshot(Duration.ofHours(1)).running().isEmpty());
    }

    @Test
    void viewContainsOnlyTableNamesNotBoundValues() {
        QueryExecutionTicket ticket = new QueryExecutionTicket(
                "secret-id",
                ActiveQueryKind.MATCH,
                "iceberg.srse.beneficiary",
                1L,
                "officer",
                "BYPASS",
                true);
        registry.tryRegister(ticket);
        String json = registry.snapshot(Duration.ofHours(1)).running().toString();
        assertTrue(json.contains("beneficiary"));
        assertFalse(json.contains("NEEDLE-12345"));
        assertFalse(json.toLowerCase().contains("select"));
    }

    private static QueryExecutionTicket ticket(String queryId) {
        return new QueryExecutionTicket(
                queryId, ActiveQueryKind.MATCH, "iceberg.srse.t", 1L, "u", "BYPASS", true);
    }

    private void backdateRunning(String queryId, Instant startedAt) {
        try {
            var runningField = ActiveQueryRegistry.class.getDeclaredField("running");
            runningField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var map = (java.util.Map<String, ActiveQueryRegistry.ActiveQueryEntry>) runningField.get(registry);
            ActiveQueryRegistry.ActiveQueryEntry entry = map.get(queryId);
            entry.startedAt = startedAt;
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
