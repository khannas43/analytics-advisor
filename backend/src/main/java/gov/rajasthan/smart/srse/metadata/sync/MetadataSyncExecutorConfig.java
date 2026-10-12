package gov.rajasthan.smart.srse.metadata.sync;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
class MetadataSyncExecutorConfig {

    @Bean(name = "metadataSyncExecutor")
    TaskExecutor metadataSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("metadata-sync-");
        // Bounded, but roomy: a bulk config import registers many tables at once. Core == max, because
        // ThreadPoolExecutor only grows past core when the queue is FULL.
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.initialize();
        return executor;
    }
}
