package com.hackathon.processvideo.infra.adapter.inbound;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hackathon.processvideo.domain.port.in.ProcessVideoUseCase;
import com.hackathon.processvideo.domain.port.out.LoggerPort;
import com.hackathon.processvideo.infra.adapter.inbound.dto.S3Bucket;
import com.hackathon.processvideo.infra.adapter.inbound.dto.S3Entity;
import com.hackathon.processvideo.infra.adapter.inbound.dto.S3EventNotification;
import com.hackathon.processvideo.infra.adapter.inbound.dto.S3EventRecord;
import com.hackathon.processvideo.infra.adapter.inbound.dto.S3Object;
import com.hackathon.processvideo.utils.JsonConverter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
class ConsumerVideoQueueTest {

    private final String payload = "{\"dummy\": \"payload\"}";
    @Mock
    private ProcessVideoUseCase processVideoUseCase;
    @Mock
    private JsonConverter jsonConverter;
    @Mock
    private LoggerPort loggerPort;
    @InjectMocks
    private ConsumerVideoQueue consumerVideoQueue;

    @BeforeEach
    void setUp() {
        // Since we use @InjectMocks, explicit setup is usually not needed unless
        // you need to handle the private static REPLICA_ID.
    }

    @Test
    @DisplayName("Should successfully process valid .mp4 video from S3 event")
    void consumeMessage_Success() {
        // Arrange
        var s3Event = createMockS3Event("my-bucket", "video.mp4");
        when(jsonConverter.toEventVideo(payload)).thenReturn(s3Event);

        // Act
        consumerVideoQueue.consumeMessage(payload);

        // Assert
        verify(processVideoUseCase, times(1)).execute("video.mp4", "my-bucket");
        verify(loggerPort).info(contains("Processing completed successfully"), any(), any(), any());
    }

    @Test
    @DisplayName("Should ignore files that do not have .mp4 extension")
    void consumeMessage_IgnoreInvalidExtension() {
        // Arrange
        var s3Event = createMockS3Event("my-bucket", "document.pdf");
        when(jsonConverter.toEventVideo(payload)).thenReturn(s3Event);

        // Act
        consumerVideoQueue.consumeMessage(payload);

        // Assert
        verify(processVideoUseCase, never()).execute(anyString(), anyString());
        verify(loggerPort).warn(contains("File ignored - not a valid video"), any(), any(), any(), eq("document.pdf"));
    }

    @Test
    @DisplayName("Should skip processing if the event key has already been processed (Deduplication)")
    void consumeMessage_SkipDuplicate() {
        // Arrange
        var s3Event = createMockS3Event("my-bucket", "unique_video.mp4");
        when(jsonConverter.toEventVideo(payload)).thenReturn(s3Event);

        // Act
        consumerVideoQueue.consumeMessage(payload); // First time
        consumerVideoQueue.consumeMessage(payload); // Second time (duplicate)

        // Assert
        verify(processVideoUseCase, times(1)).execute("unique_video.mp4", "my-bucket");
        verify(loggerPort, atLeastOnce()).warn(contains("Event already processed"), any(), any(), any(), eq("unique_video.mp4"));
    }

    @Test
    @DisplayName("Should handle NullPointerException gracefully")
    void consumeMessage_HandlesNPE() {
        // Arrange
        when(jsonConverter.toEventVideo(anyString())).thenThrow(new NullPointerException("NPE Error"));

        // Act
        consumerVideoQueue.consumeMessage(payload);

        // Assert
        verify(loggerPort).error(
                contains("Null pointer error processing message"),
                eq("[ConsumerVideoQueue][consumeMessage]"),
                anyString(),
                anyString(),
                eq("NPE Error")
        );
    }

    @Test
    @DisplayName("Should log warning when notification or records are empty")
    void consumeMessage_EmptyNotification() {
        // Arrange
        when(jsonConverter.toEventVideo(payload)).thenReturn(null);

        // Act
        consumerVideoQueue.consumeMessage(payload);

        // Assert
        verify(loggerPort).warn(contains("Notification is null"), any(), any(), any());
        verify(processVideoUseCase, never()).execute(anyString(), anyString());
    }

    // Helper method to build the complex S3 Event structure
    private S3EventNotification createMockS3Event(String bucket, String key) {
        var record = mock(S3EventRecord.class);
        var s3Data = mock(S3Entity.class);
        var bucketData = mock(S3Bucket.class);
        var objectData = mock(S3Object.class);

        when(record.getS3()).thenReturn(s3Data);
        when(s3Data.getBucket()).thenReturn(bucketData);
        when(s3Data.getObject()).thenReturn(objectData);
        when(bucketData.getName()).thenReturn(bucket);
        when(objectData.getKey()).thenReturn(key);

        var notification = mock(S3EventNotification.class);
        when(notification.getRecords()).thenReturn(List.of(record));

        return notification;
    }
}