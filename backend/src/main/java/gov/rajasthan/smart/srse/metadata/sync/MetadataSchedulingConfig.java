package gov.rajasthan.smart.srse.metadata.sync;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(MetadataSyncProperties.class)
public class MetadataSchedulingConfig {

    private final MetadataSyncProperties properties;
    private final MetadataSyncService syncService;

    public MetadataSchedulingConfig(MetadataSyncProperties properties, MetadataSyncService syncService) {
        this.properties = properties;
        this.syncService = syncService;
    }

    @Scheduled(cron = "${srse.metadata.sync.cron:0 0 2 * * SUN}")
    void scheduledMetadataSync() {
        if (!properties.sync().enabled()) {
            return;
        }
        syncService.executeScheduledSync();
    }
}
