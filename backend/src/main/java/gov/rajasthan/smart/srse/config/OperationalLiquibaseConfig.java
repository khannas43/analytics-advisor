package gov.rajasthan.smart.srse.config;

import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Runs Liquibase against the operational {@link DataSource} only. The analytical
 * plane (Presto) has no ORM and no migrations.
 */
@Configuration
@ConditionalOnProperty(name = "spring.liquibase.enabled", matchIfMissing = true)
public class OperationalLiquibaseConfig {

    @Bean
    public SpringLiquibase operationalLiquibase(
            @Qualifier("operationalDataSource") DataSource operationalDataSource,
            @Value("${spring.liquibase.change-log}") String changeLog) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(operationalDataSource);
        liquibase.setChangeLog(changeLog);
        return liquibase;
    }
}
