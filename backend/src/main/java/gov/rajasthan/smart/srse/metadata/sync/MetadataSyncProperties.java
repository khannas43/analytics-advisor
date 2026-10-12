package gov.rajasthan.smart.srse.metadata.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;

@ConfigurationProperties(prefix = "srse.metadata")
public record MetadataSyncProperties(Sync sync, Snapshot snapshot) {

    public MetadataSyncProperties {
        sync = sync == null ? Sync.defaults() : sync;
        snapshot = snapshot == null ? Snapshot.defaults() : snapshot;
    }

    public record Sync(boolean enabled, String cron) {
        public Sync {
            cron = cron == null || cron.isBlank() ? "0 0 2 * * SUN" : cron.trim();
            if (!CronExpression.isValidExpression(cron)) {
                throw new IllegalArgumentException("srse.metadata.sync.cron is not a valid cron expression: " + cron);
            }
        }

        static Sync defaults() {
            return new Sync(true, null);
        }
    }

    public record Snapshot(int ttlDays) {
        public Snapshot {
            if (ttlDays < 1) {
                throw new IllegalArgumentException("srse.metadata.snapshot.ttl-days must be at least 1");
            }
        }

        static Snapshot defaults() {
            return new Snapshot(7);
        }
    }
}
