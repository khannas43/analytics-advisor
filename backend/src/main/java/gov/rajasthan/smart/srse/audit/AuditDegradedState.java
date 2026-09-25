package gov.rajasthan.smart.srse.audit;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Signals best-effort audit write failures (§7.3 Q6). */
@Component
public class AuditDegradedState {

    public static final String FAILURE_MARKER = "SRSE_AUDIT_WRITE_FAILED";

    private final AtomicLong failureCount = new AtomicLong();
    private final AtomicReference<Instant> lastFailureAt = new AtomicReference<>();

    public void recordFailure() {
        failureCount.incrementAndGet();
        lastFailureAt.set(Instant.now());
    }

    public long failureCount() {
        return failureCount.get();
    }

    public Instant lastFailureAt() {
        return lastFailureAt.get();
    }

    public boolean isDegraded() {
        return failureCount.get() > 0;
    }
}
