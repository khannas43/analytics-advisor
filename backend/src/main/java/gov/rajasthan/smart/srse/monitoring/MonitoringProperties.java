package gov.rajasthan.smart.srse.monitoring;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "srse.monitoring")
public record MonitoringProperties(int maxRunning, int recentMinutes) {

    public MonitoringProperties {
        if (maxRunning < 1) {
            throw new IllegalArgumentException("srse.monitoring.max-running must be at least 1");
        }
        if (recentMinutes < 1) {
            throw new IllegalArgumentException("srse.monitoring.recent-minutes must be at least 1");
        }
    }
}
