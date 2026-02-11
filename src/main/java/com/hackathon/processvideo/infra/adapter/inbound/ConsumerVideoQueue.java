package com.hackathon.processvideo.infra.adapter.inbound;
import com.hackathon.processvideo.domain.port.in.ProcessVideoUseCase;
import com.hackathon.processvideo.domain.port.out.LoggerPort;
import com.hackathon.processvideo.utils.JsonConverter;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@AllArgsConstructor
public class ConsumerVideoQueue {
    private ProcessVideoUseCase processVideoUseCase;
    private JsonConverter jsonConverter;
    private LoggerPort loggerPort;
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long EVENT_CACHE_TTL_MS = 300_000; // 5 minutes
    private final AtomicLong lastCleanupTime = new AtomicLong(System.currentTimeMillis());

    // Get replica identifier from hostname or container name
    private static final String REPLICA_ID = getReplicaId();

    private static String getReplicaId() {
        try {
            String hostname = java.net.InetAddress.getLocalHost().getHostName();
            return hostname;
        } catch (Exception e) {
            return "replica-" + System.identityHashCode(new Object());
        }
    }

    @SqsListener(
            value = "${app.queues.video-process-command}",
            maxConcurrentMessages = "3"
    )
    public void consumeMessage(String payload) {
        try {
            loggerPort.debug("[ConsumerVideoQueue][consumeMessage] [Replica: {}] [Thread: {}] Received message from SQS queue, payload={}",
                REPLICA_ID, Thread.currentThread().getName(), payload);

            final var notification = jsonConverter.toEventVideo(payload);

            if (notification == null) {
                loggerPort.warn("[ConsumerVideoQueue][consumeMessage] [Replica: {}] [Thread: {}] Notification is null",
                    REPLICA_ID, Thread.currentThread().getName());
                return;
            }

            if (notification.getRecords() == null || notification.getRecords().isEmpty()) {
                loggerPort.warn("[ConsumerVideoQueue][consumeMessage] [Replica: {}] [Thread: {}] No records found in S3 event notification",
                    REPLICA_ID, Thread.currentThread().getName());
                return;
            }

            final var record = notification.getRecords().getFirst();

            if (record == null || record.getS3() == null) {
                loggerPort.warn("[ConsumerVideoQueue][consumeMessage] [Replica: {}] [Thread: {}] S3 record or S3 data is null",
                    REPLICA_ID, Thread.currentThread().getName());
                return;
            }

            final var bucketName = record.getS3().getBucket().getName();
            final var keyName = record.getS3().getObject().getKey();

            loggerPort.debug("[ConsumerVideoQueue][consumeMessage] [Replica: {}] [Thread: {}] Deserialized S3 event, bucket={}, key={}",
                REPLICA_ID, Thread.currentThread().getName(), bucketName, keyName);

            if (!isValidVideoFile(keyName)) {
                loggerPort.warn("[ConsumerVideoQueue][consumeMessage] [Replica: {}] [Thread: {}] File ignored - not a valid video, key={}",
                    REPLICA_ID, Thread.currentThread().getName(), keyName);
                return;
            }

            // Check if event already processed
            if (isEventAlreadyProcessed(keyName)) {
                loggerPort.warn("[ConsumerVideoQueue][consumeMessage] [Replica: {}] [Thread: {}] Event already processed, skipping duplicate, key={}",
                    REPLICA_ID, Thread.currentThread().getName(), keyName);
                return;
            }

            loggerPort.info("[ConsumerVideoQueue][consumeMessage] [Replica: {}] [Thread: {}] Starting video processing, bucket={}, key={}",
                REPLICA_ID, Thread.currentThread().getName(), bucketName, keyName);

            processVideoUseCase.execute(keyName, bucketName);

            // Mark event as processed
            markEventAsProcessed(keyName);

            loggerPort.info("[ConsumerVideoQueue][consumeMessage] [Replica: {}] [Thread: {}] Processing completed successfully, key={}",
                REPLICA_ID, Thread.currentThread().getName(), keyName);

        } catch (Exception e) {
            loggerPort.error("[ConsumerVideoQueue][consumeMessage] [Replica: {}] [Thread: {}] Exception processing message, error={}, stacktrace={}",
                REPLICA_ID, Thread.currentThread().getName(), e.getMessage(), e);
            // Don't throw - let SQS handle with auto acknowledgment
        }
    }

    private boolean isValidVideoFile(String keyName) {
        if (keyName == null || keyName.isEmpty()) {
            loggerPort.debug("[ConsumerVideoQueue][isValidVideoFile] Invalid keyName - null or empty");
            return false;
        }

        String lowerKeyName = keyName.toLowerCase();
        boolean isValid = lowerKeyName.endsWith(".mp4");
        loggerPort.debug("[ConsumerVideoQueue][isValidVideoFile] Validating file, key={}, isValid={}", keyName, isValid);
        return isValid;
    }

    private boolean isEventAlreadyProcessed(String eventKey) {
        // Clean up old entries periodically
        cleanupOldEntries();

        return processedEvents.containsKey(eventKey);
    }

    private void markEventAsProcessed(String eventKey) {
        long currentTime = System.currentTimeMillis();
        processedEvents.put(eventKey, currentTime);
        loggerPort.debug("[ConsumerVideoQueue][markEventAsProcessed] Event marked as processed, key={}, cacheSize={}",
            eventKey, processedEvents.size());
    }

    private void cleanupOldEntries() {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastCleanupTime.get() < 60_000) {
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