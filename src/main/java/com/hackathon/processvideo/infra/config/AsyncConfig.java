package com.hackathon.processvideo.infra.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 3;
    private static final int QUEUE_CAPACITY = 100;
    private static final int AWAIT_TERMINATION_SECONDS = 600;

    @Bean(name = "videoProcessorExecutor")
    public Executor videoProcessorExecutor() {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core threads - always active (2 videos being processed)
        executor.setCorePoolSize(CORE_POOL_SIZE);

        // Maximum threads - can scale up to 3 concurrent videos
        executor.setMaxPoolSize(MAX_POOL_SIZE);

        // Queue capacity - holds up to 100 waiting tasks
        executor.setQueueCapacity(QUEUE_CAPACITY);

        // Thread name prefix for logging
        executor.setThreadNamePrefix("video-processor-");

        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // Timeout for waiting tasks on shutdown
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);

        // Rejection policy: discard task when queue is full
        // (prevents blocking SQS consumer threads)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());

        executor.initialize();
        return executor;
    }
}

