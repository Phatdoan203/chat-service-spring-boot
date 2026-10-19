package com.side_project.chat_service.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Tang web chay tren virtual thread (spring.threads.virtual.enabled), nen o day chi
 * co executor cho hai cho can backpressure: publish outbox va fan-out WebSocket.
 * Queue co gioi han + CallerRunsPolicy: qua tai thi lam cham nguoi goi, khong OOM.
 */
@Configuration
public class AsyncConfig {

    @Bean
    public ThreadPoolTaskExecutor outboxExecutor() {
        return boundedExecutor("outbox-");
    }

    @Bean
    public ThreadPoolTaskExecutor fanoutExecutor() {
        return boundedExecutor("fanout-");
    }

    private static ThreadPoolTaskExecutor boundedExecutor(String threadNamePrefix) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
