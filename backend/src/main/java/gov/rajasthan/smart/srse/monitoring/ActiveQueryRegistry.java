package gov.rajasthan.smart.srse.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory active-query registry for this JVM only (decision 0.12). Multi-instance
 * deployments each expose their own slice; there is no shared store.
 */
@Component
public class ActiveQueryRegistry {

    private static final Logger log = LoggerFactory.getLogger(ActiveQueryRegistry.class);
    private static final int RECENT_MAX_ENTRIES = 100;

    private final MonitoringProperties properties;
    private final Map<String, ActiveQueryEntry> running = new ConcurrentHashMap<>();
    private final Deque<ActiveQueryEntry> recent = new ArrayDeque<>();
    private final AtomicInteger runningCount = new AtomicInteger();

    public ActiveQueryRegistry(MonitoringProperties properties) {
        this.properties = properties;
    }

    /**
     * @return false when the cap is reached — the query must still run, just untracked
     */
    public boolean tryRegister(QueryExecutionTicket ticket) {
        if (runningCount.get() >= properties.maxRunning()) {
            log.warn("Active query registry full ({}); not tracking {}", properties.maxRunning(), ticket.queryId());
            return false;
        }
        ActiveQueryEntry entry = ActiveQueryEntry.running(ticket);
        if (running.putIfAbsent(ticket.queryId(), entry) != null) {
            return true;
        }
        runningCount.incrementAndGet();
        return true;
    }

    public void finish(String queryId, ActiveQueryState terminalState, long durationMs) {
        ActiveQueryEntry entry = running.remove(queryId);
        if (entry == null) {
            return;
        }
        runningCount.decrementAndGet();
        ActiveQueryEntry finished = entry.finished(terminalState, durationMs, Instant.now());
        synchronized (recent) {
            recent.addFirst(finished);
            trimRecentLocked(Instant.now());
        }
    }

    public ActiveQueriesSnapshot snapshot(Duration abandonedAfter) {
        Instant now = Instant.now();
        sweepAbandoned(now, abandonedAfter);
        trimRecent(now);
        List<ActiveQueryView> runningViews = running.values().stream()
                .map(ActiveQueryEntry::toView)
                .sorted(java.util.Comparator.comparing(ActiveQueryView::startedAt))
                .toList();
        List<ActiveQueryView> recentViews;
        synchronized (recent) {
            recentViews = recent.stream().map(ActiveQueryEntry::toView).toList();
        }
        return new ActiveQueriesSnapshot(runningViews, recentViews);
    }

    private void sweepAbandoned(Instant now, Duration abandonedAfter) {
        for (Map.Entry<String, ActiveQueryEntry> e : running.entrySet()) {
            ActiveQueryEntry entry = e.getValue();
            if (Duration.between(entry.startedAt, now).compareTo(abandonedAfter) > 0) {
                finish(e.getKey(), ActiveQueryState.ABANDONED, Duration.between(entry.startedAt, now).toMillis());
            }
        }
    }

    private void trimRecent(Instant now) {
        synchronized (recent) {
            trimRecentLocked(now);
        }
    }

    private void trimRecentLocked(Instant now) {
        Instant cutoff = now.minus(Duration.ofMinutes(properties.recentMinutes()));
        while (!recent.isEmpty()) {
            ActiveQueryEntry tail = recent.peekLast();
            if (tail == null) {
                break;
            }
            if (recent.size() > RECENT_MAX_ENTRIES
                    || (tail.finishedAt != null && tail.finishedAt.isBefore(cutoff))) {
                recent.removeLast();
            } else {
                break;
            }
        }
    }

    static final class ActiveQueryEntry {
        final String queryId;
        final String username;
        final ActiveQueryKind kind;
        final String qualifiedTables;
        ActiveQueryState state;
        Instant startedAt;
        Instant finishedAt;
        Long durationMs;

        ActiveQueryEntry(
                String queryId,
                String username,
                ActiveQueryKind kind,
                String qualifiedTables,
                ActiveQueryState state,
                Instant startedAt,
                Instant finishedAt,
                Long durationMs) {
            this.queryId = queryId;
            this.username = username;
            this.kind = kind;
            this.qualifiedTables = qualifiedTables;
            this.state = state;
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
            this.durationMs = durationMs;
        }

        static ActiveQueryEntry running(QueryExecutionTicket ticket) {
            return new ActiveQueryEntry(
                    ticket.queryId(),
                    ticket.actorUsername(),
                    ticket.kind(),
                    ticket.qualifiedTables(),
                    ActiveQueryState.RUNNING,
                    Instant.now(),
                    null,
                    null);
        }

        ActiveQueryEntry finished(ActiveQueryState terminal, long durationMs, Instant finishedAt) {
            return new ActiveQueryEntry(
                    queryId, username, kind, qualifiedTables, terminal, startedAt, finishedAt, durationMs);
        }

        ActiveQueryView toView() {
            return new ActiveQueryView(
                    queryId,
                    username,
                    kind,
                    qualifiedTables,
                    state.name(),
                    startedAt.toString(),
                    durationMs);
        }
    }

    public record ActiveQueryView(
            String queryId,
            String username,
            ActiveQueryKind kind,
            String qualifiedTables,
            String state,
            String startedAt,
            Long durationMs) {
    }

    public record ActiveQueriesSnapshot(List<ActiveQueryView> running, List<ActiveQueryView> recent) {
    }
}
