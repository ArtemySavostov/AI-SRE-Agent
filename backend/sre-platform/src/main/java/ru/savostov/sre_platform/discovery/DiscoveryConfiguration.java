package ru.savostov.sre_platform.discovery;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class DiscoveryConfiguration {
    @Bean("discoveryExecutor")
    public ThreadPoolTaskExecutor discoveryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("discovery-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(70);
        return executor;
    }
    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService sshDeadlines() {
        return Executors.newScheduledThreadPool(2, Thread.ofPlatform().daemon().name("ssh-deadline-", 0).factory());
    }
}
