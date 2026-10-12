package gov.rajasthan.smart.srse.metadata.sync;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

final class MetadataSnapshotSupport {

    private MetadataSnapshotSupport() {
    }

    static boolean isFresh(Instant syncedAt, int ttlDays) {
        if (syncedAt == null) {
            return false;
        }
        Instant cutoff = Instant.now().minus(ttlDays, ChronoUnit.DAYS);
        return !syncedAt.isBefore(cutoff);
    }

    /** Admin-visible only — never expose hostnames, credentials or stack traces. */
    static String redactSyncError(Throwable ex) {
        if (ex == null) {
            return "Sync failed";
        }
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        String trimmed = message.trim();
        if (trimmed.length() > 480) {
            trimmed = trimmed.substring(0, 480);
        }
        return trimmed.replaceAll("(?i)(password|secret|jdbc:[^\\s]+)", "[redacted]");
    }
}
