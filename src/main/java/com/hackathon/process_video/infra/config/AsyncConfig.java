package com.hackathon.process_video.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "videoProcessorExecutor")
    public Executor videoProcessorExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core threads - always active (2 videos being processed)
        executor.setCorePoolSize(2);

        // Maximum threads - can scale up to 3 concurrent videos
        executor.setMaxPoolSize(3);

        // Queue capacity - holds up to 100 waiting tasks
        executor.setQueueCapacity(100);

        // Thread name prefix for logging
        executor.setThreadNamePrefix("video-processor-");

        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // Timeout for waiting tasks on shutdown
        executor.setAwaitTerminationSeconds(600);

        // Rejection policy: discard task when queue is full
        // (prevents blocking SQS consumer threads)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());

        executor.initialize();
        return executor;
    }
}

