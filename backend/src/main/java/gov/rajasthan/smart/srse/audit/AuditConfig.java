package gov.rajasthan.smart.srse.audit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AuditProperties.class)
public class AuditConfig {
}
