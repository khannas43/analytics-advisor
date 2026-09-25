package gov.rajasthan.smart.srse.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "srse.audit")
public record AuditProperties(List<String> trustedProxies, int retentionDays) {

    public AuditProperties {
        trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
        if (retentionDays <= 0) {
            retentionDays = 365;
        }
    }
}
