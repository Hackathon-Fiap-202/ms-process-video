package com.hackathon.process_video.infra.adapter.inbound;
import com.hackathon.process_video.domain.port.in.ProcessVideoUseCase;
import com.hackathon.process_video.domain.port.out.LoggerPort;
import com.hackathon.process_video.utils.JsonConverter;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class ConsumerVideoQueue {
    private ProcessVideoUseCase processVideoUseCase;
    private JsonConverter jsonConverter;
    private LoggerPort loggerPort;

    @SqsListener(
            value = "${app.queues.video-process-command}",
            acknowledgementMode = "MANUAL"
    )
    public void consumeMessage(String payload, Acknowledgement acknowledgement) {
        try {
            loggerPort.debug("[ConsumerVideoQueue][consumeMessage] Received message from SQS queue, payload={}", payload);

            final var notification = jsonConverter.toEventVideo(payload);

            if (notification == null) {
                loggerPort.warn("[ConsumerVideoQueue][consumeMessage] Notification is null");
                acknowledgement.acknowledge();
                return;
            }

            if (notification.getRecords() == null || notification.getRecords().isEmpty()) {
                loggerPort.warn("[ConsumerVideoQueue][consumeMessage] No records found in S3 event notification");
                acknowledgement.acknowledge();
                return;
            }

            final var record = notification.getRecords().getFirst();

            if (record == null || record.getS3() == null) {
                loggerPort.warn("[ConsumerVideoQueue][consumeMessage] S3 record or S3 data is null");
                acknowledgement.acknowledge();
                return;
            }

            final var bucketName = record.getS3().getBucket().getName();
            final var keyName = record.getS3().getObject().getKey();

            loggerPort.debug("[ConsumerVideoQueue][consumeMessage] Deserialized S3 event, bucket={}, key={}", bucketName, keyName);

            if (!isValidVideoFile(keyName)) {
                loggerPort.warn("[ConsumerVideoQueue][consumeMessage] File ignored - not a valid video, key={}", keyName);
                acknowledgement.acknowledge();
                return;
            }

            loggerPort.info("[ConsumerVideoQueue][consumeMessage] Starting video processing, bucket={}, key={}", bucketName, keyName);

            processVideoUseCase.execute(keyName, bucketName);

            acknowledgement.acknowledge();

            loggerPort.info("[ConsumerVideoQueue][consumeMessage] Processing completed successfully, key={}", keyName);

        } catch (Exception e) {
            loggerPort.error("[ConsumerVideoQueue][consumeMessage] Exception processing message, error={}, stacktrace={}", e.getMessage(), e);
            throw e;
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
}