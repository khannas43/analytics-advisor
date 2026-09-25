package gov.rajasthan.smart.srse.lakehouse;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "srse.scope")
public record ScopeFilterProperties(int maxInValues) {
    public ScopeFilterProperties {
        if (maxInValues <= 0) {
            maxInValues = 1000;
        }
    }
}
