package com.hackathon.processvideo.infra.adapter.inbound;

import com.hackathon.processvideo.domain.port.in.ProcessVideoUseCase;
import com.hackathon.processvideo.domain.port.out.LoggerPort;
import com.hackathon.processvideo.utils.JsonConverter;
import io.awspring.cloud.sqs.annotation.SqsListener;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class ConsumerVideoQueue {
    private static final long EVENT_CACHE_TTL_MS = 300_000; // 5 minutes
    private static final long CLEANUP_INTERVAL_MS = 60_000; // 1 minute
    private static final String LOG_PREFIX_CONSUME = "[ConsumerVideoQueue][consumeMessage]";
    private static final String LOG_PREFIX_PROCESS = "[ConsumerVideoQueue][processValidMessage]";
    private static final String LOG_PREFIX_FORMAT = "{} [Replica: {}] [Thread: {}] ";
    // Get replica identifier from hostname or container name
    private static final String REPLICA_ID = getReplicaId();
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupTime = new AtomicLong(System.currentTimeMillis());
    private ProcessVideoUseCase processVideoUseCase;
    private JsonConverter jsonConverter;
    private LoggerPort loggerPort;

    private static String getReplicaId() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "replica-" + System.identityHashCode(new Object());
        }
    }

    @SqsListener(
            value = "${app.queues.video-process-command}",
            maxConcurrentMessages = "3"
    )
    public void consumeMessage(String payload) {
        try {
            loggerPort.debug(LOG_PREFIX_CONSUME
                            + LOG_PREFIX_FORMAT
                            + "Received message from SQS queue, payload={}",
                    LOG_PREFIX_CONSUME,
                    REPLICA_ID,
                    Thread.currentThread().getName(),
                    payload);

            processValidMessage(payload);

            loggerPort.info(LOG_PREFIX_CONSUME + LOG_PREFIX_FORMAT + "Processing completed successfully",
                    LOG_PREFIX_CONSUME, REPLICA_ID, Thread.currentThread().getName());

        } catch (NullPointerException e) {
            loggerPort.error(LOG_PREFIX_CONSUME + LOG_PREFIX_FORMAT + "Null pointer error processing message, error={}",
                    LOG_PREFIX_CONSUME, REPLICA_ID, Thread.currentThread().getName(), e.getMessage());
        } catch (IllegalArgumentException e) {
            loggerPort.error(LOG_PREFIX_CONSUME + LOG_PREFIX_FORMAT + "Invalid argument error processing message, error={}",
                    LOG_PREFIX_CONSUME, REPLICA_ID, Thread.currentThread().getName(), e.getMessage());
        }
    }

    private void processValidMessage(String payload) {
        final var notification = jsonConverter.toEventVideo(payload);

        if (notification == null) {
            loggerPort.warn(LOG_PREFIX_PROCESS + LOG_PREFIX_FORMAT + "Notification is null",
                    LOG_PREFIX_PROCESS, REPLICA_ID, Thread.currentThread().getName());
            return;
        }

        if (notification.getRecords() == null || notification.getRecords().isEmpty()) {
            loggerPort.warn(LOG_PREFIX_PROCESS + LOG_PREFIX_FORMAT
                            + "No records found in S3 event notification",
                    LOG_PREFIX_PROCESS, REPLICA_ID, Thread.currentThread().getName());
            return;
        }

        final var s3EventRecord = notification.getRecords().getFirst();

        if (s3EventRecord == null || s3EventRecord.getS3() == null) {
            loggerPort.warn(LOG_PREFIX_PROCESS + LOG_PREFIX_FORMAT + "S3 record or S3 data is null",
                    LOG_PREFIX_PROCESS, REPLICA_ID, Thread.currentThread().getName());
            return;
        }

        final var bucketName = s3EventRecord.getS3().getBucket().getName();
        final var keyName = s3EventRecord.getS3().getObject().getKey();

        loggerPort.debug(LOG_PREFIX_PROCESS + LOG_PREFIX_FORMAT + "Deserialized S3 event, bucket={}, key={}",
                LOG_PREFIX_PROCESS, REPLICA_ID, Thread.currentThread().getName(), bucketName, keyName);

        if (!isValidVideoFile(keyName)) {
            loggerPort.warn(LOG_PREFIX_PROCESS + LOG_PREFIX_FORMAT + "File ignored - not a valid video, key={}",
                    LOG_PREFIX_PROCESS, REPLICA_ID, Thread.currentThread().getName(), keyName);
            return;
        }

        if (isEventAlreadyProcessed(keyName)) {
            loggerPort.warn(LOG_PREFIX_PROCESS + LOG_PREFIX_FORMAT
                            + "Event already processed, skipping duplicate, key={}",
                    LOG_PREFIX_PROCESS, REPLICA_ID, Thread.currentThread().getName(), keyName);
            return;
        }

        loggerPort.info(LOG_PREFIX_PROCESS + LOG_PREFIX_FORMAT + "Starting video processing, bucket={}, key={}",
                LOG_PREFIX_PROCESS, REPLICA_ID, Thread.currentThread().getName(), bucketName, keyName);

        processVideoUseCase.execute(keyName, bucketName);
        markEventAsProcessed(keyName);
    }

    private boolean isValidVideoFile(String keyName) {
        if (keyName == null || keyName.isEmpty()) {
            loggerPort.debug("[ConsumerVideoQueue][isValidVideoFile] Invalid keyName - null or empty");
            return false;
        }

        final String lowerKeyName = keyName.toLowerCase();
        final boolean isValid = lowerKeyName.endsWith(".mp4");
        loggerPort.debug("[ConsumerVideoQueue][isValidVideoFile] Validating file, key={}, isValid={}", keyName, isValid);
        return isValid;
    }

    private boolean isEventAlreadyProcessed(String eventKey) {
        // Clean up old entries periodically
        cleanupOldEntries();

        return processedEvents.containsKey(eventKey);
    }

    private void markEventAsProcessed(String eventKey) {
        final long currentTime = System.currentTimeMillis();
        processedEvents.put(eventKey, currentTime);
        loggerPort.debug("[ConsumerVideoQueue][markEventAsProcessed] Event marked as processed, key={}, cacheSize={}",
                eventKey, processedEvents.size());
    }

    private void cleanupOldEntries() {
        final long currentTime = System.currentTimeMillis();

        if (currentTime - lastCleanupTime.get() < CLEANUP_INTERVAL_MS) {
            return;
        }

        lastCleanupTime.set(currentTime);

        processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > EVENT_CACHE_TTL_MS
        );

        loggerPort.debug("[ConsumerVideoQueue][cleanupOldEntries] Cleaned up old entries, remainingCacheSize={}",
                processedEvents.size());
    }
}
