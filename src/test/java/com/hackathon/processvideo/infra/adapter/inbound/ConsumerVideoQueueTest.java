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
import org.springframework.test.util.ReflectionTestUtils;
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
        var s3Event = createMockS3Event("video.mp4");
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
        var s3Event = createMockS3Event("document.pdf");
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
        var s3Event = createMockS3Event("unique_video.mp4");
        when(jsonConverter.toEventVideo(payload)).thenReturn(s3Event);

        // Act
        consumerVideoQueue.consumeMessage(payload); // First time
        consumerVideoQueue.consumeMessage(payload); // Second time (duplicate)

        // Assert
        verify(processVideoUseCase, times(1)).execute("unique_video.mp4", "my-bucket");
        verify(loggerPort, atLeastOnce()).warn(contains("Event already processed"), any(), any(), any(),
                eq("unique_video.mp4"));
    }

    @Test
    @DisplayName("Should handle NullPointerException during message processing")
    void consumeMessage_NullPointerException() {
        // Arrange
        when(jsonConverter.toEventVideo(anyString())).thenThrow(new NullPointerException("Null value encountered"));

        // Act
        consumerVideoQueue.consumeMessage(payload);

        // Assert
        org.mockito.ArgumentCaptor<String> msgCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Object> arg1 = org.mockito.ArgumentCaptor.forClass(Object.class);
        org.mockito.ArgumentCaptor<Object> arg2 = org.mockito.ArgumentCaptor.forClass(Object.class);
        org.mockito.ArgumentCaptor<Object> arg3 = org.mockito.ArgumentCaptor.forClass(Object.class);
        org.mockito.ArgumentCaptor<Object> arg4 = org.mockito.ArgumentCaptor.forClass(Object.class);

        // Verify error was called with String and 4 arguments
        verify(loggerPort, atLeastOnce()).error(msgCaptor.capture(), arg1.capture(), arg2.capture(), arg3.capture(), arg4.capture());
        
        // Check message contains expected text
        org.junit.jupiter.api.Assertions.assertTrue(msgCaptor.getValue().contains("Null pointer error processing message"));
        
        verify(processVideoUseCase, never()).execute(anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle IllegalArgumentException during message processing")
    void consumeMessage_IllegalArgumentException() {
        // Arrange
        when(jsonConverter.toEventVideo(anyString())).thenThrow(new IllegalArgumentException("Invalid payload format"));

        // Act
        consumerVideoQueue.consumeMessage(payload);

        // Assert
        org.mockito.ArgumentCaptor<String> msgCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Object> arg1 = org.mockito.ArgumentCaptor.forClass(Object.class);
        org.mockito.ArgumentCaptor<Object> arg2 = org.mockito.ArgumentCaptor.forClass(Object.class);
        org.mockito.ArgumentCaptor<Object> arg3 = org.mockito.ArgumentCaptor.forClass(Object.class);
        org.mockito.ArgumentCaptor<Object> arg4 = org.mockito.ArgumentCaptor.forClass(Object.class);

        verify(loggerPort, atLeastOnce()).error(msgCaptor.capture(), arg1.capture(), arg2.capture(), arg3.capture(), arg4.capture());
        
        org.junit.jupiter.api.Assertions.assertTrue(msgCaptor.getValue().contains("Invalid argument error processing message"));
        
        verify(processVideoUseCase, never()).execute(anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle null notification from JSON converter")
    void consumeMessage_NullNotification() {
        // Arrange
        when(jsonConverter.toEventVideo(payload)).thenReturn(null);

        // Act
        consumerVideoQueue.consumeMessage(payload);

        // Assert
        verify(loggerPort).warn(contains("Notification is null"), any(), any(), any());
        verify(processVideoUseCase, never()).execute(anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle empty records list in notification")
    void consumeMessage_EmptyRecords() {
        // Arrange
        var notification = mock(S3EventNotification.class);
        when(jsonConverter.toEventVideo(payload)).thenReturn(notification);
        when(notification.getRecords()).thenReturn(List.of()); // Empty list

        // Act
        consumerVideoQueue.consumeMessage(payload);

        // Assert
        verify(loggerPort).warn(contains("No records found in S3 event notification"), any(), any(), any());
        verify(processVideoUseCase, never()).execute(anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle null records list in notification")
    void consumeMessage_NullRecords() {
        // Arrange
        var notification = mock(S3EventNotification.class);
        when(jsonConverter.toEventVideo(payload)).thenReturn(notification);
        when(notification.getRecords()).thenReturn(null); // Null records list

        // Act
        consumerVideoQueue.consumeMessage(payload);

        // Assert
        verify(loggerPort).warn(contains("No records found in S3 event notification"), any(), any(), any());
        verify(processVideoUseCase, never()).execute(anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle null first record in S3 event records list")
    void consumeMessage_NullFirstRecord() {
        // Arrange
        var notification = mock(S3EventNotification.class);
        when(jsonConverter.toEventVideo(payload)).thenReturn(notification);
        final List<S3EventRecord> recordsWithNull = new java.util.ArrayList<>();
        recordsWithNull.add(null); // First element is null
        when(notification.getRecords()).thenReturn(recordsWithNull);

        // Act
        consumerVideoQueue.consumeMessage(payload);

        // Assert
        verify(loggerPort).warn(contains("S3 record or S3 data is null"), any(), any(), any());
        verify(processVideoUseCase, never()).execute(anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle null S3 data in event record")
    void consumeMessage_NullS3Data() {
        // Arrange
        var eventRecord = mock(S3EventRecord.class);
        var notification = mock(S3EventNotification.class);

        when(jsonConverter.toEventVideo(payload)).thenReturn(notification);
        when(notification.getRecords()).thenReturn(List.of(eventRecord));
        when(eventRecord.getS3()).thenReturn(null); // Null S3 data

        // Act
        consumerVideoQueue.consumeMessage(payload);

        // Assert
        verify(loggerPort).warn(contains("S3 record or S3 data is null"), any(), any(), any());
        verify(processVideoUseCase, never()).execute(anyString(), anyString());
    }

    @Test
    @DisplayName("Should not evict fresh cache entries during cleanup")
    void consumeMessage_FreshEntryNotEvictedDuringCleanup() {
        // Arrange — process a video to cache it
        var s3Event = createMockS3Event("fresh-video.mp4");
        when(jsonConverter.toEventVideo(payload)).thenReturn(s3Event);
        consumerVideoQueue.consumeMessage(payload);
        verify(processVideoUseCase, times(1)).execute("fresh-video.mp4", "my-bucket");

        // Force lastCleanupTime to be old enough to trigger a cleanup run,
        // but leave the cache entry timestamp as recent (current time)
        final java.util.concurrent.atomic.AtomicLong lastCleanup =
                (java.util.concurrent.atomic.AtomicLong)
                        ReflectionTestUtils.getField(consumerVideoQueue, "lastCleanupTime");
        lastCleanup.set(System.currentTimeMillis() - 120_000L); // trigger cleanup on next call

        // Act — second call: cleanup runs but entry is fresh, so it is NOT evicted
        consumerVideoQueue.consumeMessage(payload);

        // Assert — still deduplicated (entry survived cleanup because it was fresh)
        verify(processVideoUseCase, times(1)).execute("fresh-video.mp4", "my-bucket");
    }

    @Test
    @DisplayName("Should ignore message with null key in S3 object")
    void consumeMessage_NullKey() {
        // Arrange
        var s3Event = createMockS3Event(null);
        when(jsonConverter.toEventVideo(payload)).thenReturn(s3Event);

        // Act
        consumerVideoQueue.consumeMessage(payload);

        // Assert
        verify(processVideoUseCase, never()).execute(anyString(), anyString());
    }

    @Test
    @DisplayName("Should ignore message with empty key in S3 object")
    void consumeMessage_EmptyKey() {
        // Arrange
        var s3Event = createMockS3Event("");
        when(jsonConverter.toEventVideo(payload)).thenReturn(s3Event);

        // Act
        consumerVideoQueue.consumeMessage(payload);

        // Assert
        verify(processVideoUseCase, never()).execute(anyString(), anyString());
    }

    @Test
    @DisplayName("Should clean up expired cache entries after TTL and process event again")
    void consumeMessage_CleanupExpiredCacheEntries() {
        // Arrange
        var s3Event = createMockS3Event("expiry-video.mp4");
        when(jsonConverter.toEventVideo(payload)).thenReturn(s3Event);

        // First call — mark as processed
        consumerVideoQueue.consumeMessage(payload);
        verify(processVideoUseCase, times(1)).execute("expiry-video.mp4", "my-bucket");

        // Simulate cache TTL expiry by setting lastCleanupTime far in the past
        // and backdating the cache entry so it appears expired
        final long expiredTimestamp = System.currentTimeMillis() - 400_000L; // older than 5-min TTL
        @SuppressWarnings("unchecked")
        final java.util.concurrent.ConcurrentHashMap<String, Long> cache =
                (java.util.concurrent.ConcurrentHashMap<String, Long>)
                        ReflectionTestUtils.getField(consumerVideoQueue, "processedEvents");
        cache.put("expiry-video.mp4", expiredTimestamp);

        // Force lastCleanupTime to be old enough to trigger cleanup
        final java.util.concurrent.atomic.AtomicLong lastCleanup =
                (java.util.concurrent.atomic.AtomicLong)
                        ReflectionTestUtils.getField(consumerVideoQueue, "lastCleanupTime");
        lastCleanup.set(System.currentTimeMillis() - 120_000L); // 2 minutes ago

        // Act — second call should NOT be deduplicated since the cache entry expired
        consumerVideoQueue.consumeMessage(payload);

        // Assert — processed again after cache eviction
        verify(processVideoUseCase, times(2)).execute("expiry-video.mp4", "my-bucket");
    }

    // Helper method to build the complex S3 Event structure
    private S3EventNotification createMockS3Event(String key) {
        var eventRecord = mock(S3EventRecord.class);
        var s3Data = mock(S3Entity.class);
        var bucketData = mock(S3Bucket.class);
        var objectData = mock(S3Object.class);

        when(eventRecord.getS3()).thenReturn(s3Data);
        when(s3Data.getBucket()).thenReturn(bucketData);
        when(s3Data.getObject()).thenReturn(objectData);
        when(bucketData.getName()).thenReturn("my-bucket");
        when(objectData.getKey()).thenReturn(key);

        var notification = mock(S3EventNotification.class);
        when(notification.getRecords()).thenReturn(List.of(eventRecord));

        return notification;
    }
}