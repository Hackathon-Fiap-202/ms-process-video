package com.hackathon.processvideo.domain.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hackathon.processvideo.domain.exception.FileNotExistException;
import com.hackathon.processvideo.domain.exception.VideoProcessingException;
import com.hackathon.processvideo.domain.port.out.FileServicePort;
import com.hackathon.processvideo.domain.port.out.LoggerPort;
import com.hackathon.processvideo.domain.port.out.VideoFrameExtractorPort;
import com.hackathon.processvideo.domain.port.out.VideoStatusUpdatePort;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Comprehensive unit tests for VideoProcessorService using JUnit 5 and Mockito.
 * <p>
 * Tests cover:
 * - Success path: Valid video processing from retrieval to deletion
 * - Failure scenarios: Extraction and upload failures
 * - Status notifications: Verification of correct status updates
 * - File cleanup: Proper resource management
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VideoProcessorService Unit Tests")
class VideoProcessorServiceTest {

    private static final String INPUT_BUCKET = "input-videos";
    private static final String OUTPUT_BUCKET = "output-videos";
    private static final String OUTPUT_PREFIX = "video-processed-storage/";
    private static final String VIDEO_KEY = "videos/sample-video.mp4";
    private static final String OUTPUT_KEY = "video-processed-storage/end-process/sample-video.zip";
    private static final String ENTRY_PREFIX = "sample-video";
    private static final long ARCHIVE_SIZE = 2_560_000; // 2.5 MB (approximately 50 frames)
    private static final int EXPECTED_FRAME_COUNT = 50; // 2_560_000 / (50 * 1024)

    @Mock
    private FileServicePort fileServicePort;

    @Mock
    private VideoFrameExtractorPort videoFrameExtractorPort;

    @Mock
    private VideoStatusUpdatePort videoStatusUpdatePort;

    @Mock
    private LoggerPort loggerPort;

    private VideoProcessorService videoProcessorService;

    @BeforeEach
    void setUp() {
        videoProcessorService = new VideoProcessorService(
                fileServicePort,
                videoFrameExtractorPort,
                videoStatusUpdatePort,
                loggerPort,
                OUTPUT_BUCKET,
                OUTPUT_PREFIX
        );
    }

    @Nested
    @DisplayName("Success Path Tests")
    class SuccessPathTests {

        @Test
        @DisplayName("Should successfully process video: retrieve, extract frames, upload, and delete source")
        void shouldSuccessfullyProcessVideo() throws IOException {
            // Arrange
            final InputStream videoStream = new ByteArrayInputStream("video content".getBytes());
            final InputStream zippedFrames = new ByteArrayInputStream("zipped content".getBytes());

            when(fileServicePort.getFile(INPUT_BUCKET, VIDEO_KEY))
                    .thenReturn(videoStream);
            when(videoFrameExtractorPort.extractFramesAsZip(videoStream, ENTRY_PREFIX))
                    .thenReturn(zippedFrames);
            when(fileServicePort.uploadFile(OUTPUT_BUCKET, OUTPUT_KEY, zippedFrames))
                    .thenReturn(true);
            when(fileServicePort.getSize(OUTPUT_BUCKET, OUTPUT_KEY))
                    .thenReturn(ARCHIVE_SIZE);

            // Act
            videoProcessorService.execute(VIDEO_KEY, INPUT_BUCKET);

            // Assert
            InOrder inOrder = inOrder(fileServicePort, videoFrameExtractorPort, videoStatusUpdatePort);

            // Verify file retrieval
            inOrder.verify(fileServicePort).getFile(INPUT_BUCKET, VIDEO_KEY);

            // Verify frame extraction
            inOrder.verify(videoFrameExtractorPort).extractFramesAsZip(any(InputStream.class), eq(ENTRY_PREFIX));

            // Verify upload
            inOrder.verify(fileServicePort).uploadFile(eq(OUTPUT_BUCKET), eq(OUTPUT_KEY), any(InputStream.class));

            // Verify file size retrieval
            inOrder.verify(fileServicePort).getSize(OUTPUT_BUCKET, OUTPUT_KEY);

            // Verify source deletion
            inOrder.verify(fileServicePort).deleteFile(INPUT_BUCKET, VIDEO_KEY);

            // Verify status notification with success
            inOrder.verify(videoStatusUpdatePort).notifyStatus(VIDEO_KEY, true, EXPECTED_FRAME_COUNT, ARCHIVE_SIZE);
        }

        @Test
        @DisplayName("Should correctly format output key from input key")
        void shouldFormatOutputKeyCorrectly() throws IOException {
            // Arrange
            final InputStream videoStream = new ByteArrayInputStream("video".getBytes());
            final InputStream zippedFrames = new ByteArrayInputStream("zipped".getBytes());

            when(fileServicePort.getFile(INPUT_BUCKET, VIDEO_KEY))
                    .thenReturn(videoStream);
            when(videoFrameExtractorPort.extractFramesAsZip(videoStream, ENTRY_PREFIX))
                    .thenReturn(zippedFrames);
            when(fileServicePort.uploadFile(OUTPUT_BUCKET, OUTPUT_KEY, zippedFrames))
                    .thenReturn(true);
            when(fileServicePort.getSize(OUTPUT_BUCKET, OUTPUT_KEY))
                    .thenReturn(ARCHIVE_SIZE);

            // Act
            videoProcessorService.execute(VIDEO_KEY, INPUT_BUCKET);

            // Assert
            verify(fileServicePort).uploadFile(eq(OUTPUT_BUCKET), eq("video-processed-storage/end-process/sample-video.zip"), any(InputStream.class));
        }

        @Test
        @DisplayName("Should correctly estimate frame count from archive size")
        void shouldCorrectlyEstimateFrameCount() throws IOException {
            // Arrange
            final long archiveSize = 5_120_000; // Should result in 100 frames
            final InputStream videoStream = new ByteArrayInputStream("video".getBytes());
            final InputStream zippedFrames = new ByteArrayInputStream("zipped".getBytes());

            when(fileServicePort.getFile(INPUT_BUCKET, VIDEO_KEY))
                    .thenReturn(videoStream);
            when(videoFrameExtractorPort.extractFramesAsZip(videoStream, ENTRY_PREFIX))
                    .thenReturn(zippedFrames);
            when(fileServicePort.uploadFile(OUTPUT_BUCKET, OUTPUT_KEY, zippedFrames))
                    .thenReturn(true);
            when(fileServicePort.getSize(OUTPUT_BUCKET, OUTPUT_KEY))
                    .thenReturn(archiveSize);

            // Act
            videoProcessorService.execute(VIDEO_KEY, INPUT_BUCKET);

            // Assert
            final int expectedFrames = 100; // 5_120_000 / (50 * 1024)
            verify(videoStatusUpdatePort).notifyStatus(VIDEO_KEY, true, expectedFrames, archiveSize);
        }

        @Test
        @DisplayName("Should handle video key with nested path correctly")
        void shouldHandleNestedVideoPath() throws IOException {
            // Arrange
            final String nestedVideoKey = "path/to/videos/nested-video.mp4";
            final String expectedOutputKey = "video-processed-storage/end-process/nested-video.zip";
            final String expectedPrefix = "nested-video";

            final InputStream videoStream = new ByteArrayInputStream("video".getBytes());
            final InputStream zippedFrames = new ByteArrayInputStream("zipped".getBytes());

            when(fileServicePort.getFile(INPUT_BUCKET, nestedVideoKey))
                    .thenReturn(videoStream);
            when(videoFrameExtractorPort.extractFramesAsZip(videoStream, expectedPrefix))
                    .thenReturn(zippedFrames);
            when(fileServicePort.uploadFile(OUTPUT_BUCKET, expectedOutputKey, zippedFrames))
                    .thenReturn(true);
            when(fileServicePort.getSize(OUTPUT_BUCKET, expectedOutputKey))
                    .thenReturn(ARCHIVE_SIZE);

            // Act
            videoProcessorService.execute(nestedVideoKey, INPUT_BUCKET);

            // Assert
            verify(videoFrameExtractorPort).extractFramesAsZip(any(InputStream.class), eq(expectedPrefix));
            verify(fileServicePort).uploadFile(eq(OUTPUT_BUCKET), eq(expectedOutputKey), any(InputStream.class));
        }
    }

    @Nested
    @DisplayName("Frame Extraction Failure Tests")
    class ExtractionFailureTests {

        @Test
        @DisplayName("Should handle IOException during frame extraction gracefully")
        void shouldHandleExtractionIOException() throws IOException {
            // Arrange
            final InputStream videoStream = new ByteArrayInputStream("video".getBytes());

            when(fileServicePort.getFile(INPUT_BUCKET, VIDEO_KEY))
                    .thenReturn(videoStream);
            when(videoFrameExtractorPort.extractFramesAsZip(any(InputStream.class), anyString()))
                    .thenThrow(new VideoProcessingException("Frame extraction failed", null));

            // Act
            videoProcessorService.execute(VIDEO_KEY, INPUT_BUCKET);

            // Assert
            // Upload and deletion should not occur
            verify(fileServicePort, never()).uploadFile(anyString(), anyString(), any(InputStream.class));
            verify(fileServicePort, never()).deleteFile(INPUT_BUCKET, VIDEO_KEY);

            // Status notification should report failure
            verify(videoStatusUpdatePort).notifyStatus(eq(VIDEO_KEY), eq(false), eq(0), eq(0L));

            // Error should be logged
            verify(loggerPort).error(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should handle VideoProcessingException during frame extraction")
        void shouldHandleExtractionVideoProcessingException() throws IOException {
            // Arrange
            final InputStream videoStream = new ByteArrayInputStream("video".getBytes());

            when(fileServicePort.getFile(INPUT_BUCKET, VIDEO_KEY))
                    .thenReturn(videoStream);
            when(videoFrameExtractorPort.extractFramesAsZip(videoStream, ENTRY_PREFIX))
                    .thenThrow(new VideoProcessingException("Invalid video format", null));

            // Act
            videoProcessorService.execute(VIDEO_KEY, INPUT_BUCKET);

            // Assert
            verify(fileServicePort, never()).uploadFile(anyString(), anyString(), any(InputStream.class));
            verify(fileServicePort, never()).deleteFile(INPUT_BUCKET, VIDEO_KEY);
            verify(videoStatusUpdatePort).notifyStatus(eq(VIDEO_KEY), eq(false), eq(0), eq(0L));
        }
    }

    @Nested
    @DisplayName("File Upload Failure Tests")
    class UploadFailureTests {

        @Test
        @DisplayName("Should handle upload failure and not delete source file")
        void shouldHandleUploadFailure() throws IOException {
            // Arrange
            final InputStream videoStream = new ByteArrayInputStream("video".getBytes());
            final InputStream zippedFrames = new ByteArrayInputStream("zipped".getBytes());

            when(fileServicePort.getFile(INPUT_BUCKET, VIDEO_KEY))
                    .thenReturn(videoStream);
            when(videoFrameExtractorPort.extractFramesAsZip(videoStream, ENTRY_PREFIX))
                    .thenReturn(zippedFrames);
            when(fileServicePort.uploadFile(OUTPUT_BUCKET, OUTPUT_KEY, zippedFrames))
                    .thenReturn(false); // Upload fails

            // Act
            videoProcessorService.execute(VIDEO_KEY, INPUT_BUCKET);

            // Assert
            // Source file should NOT be deleted if upload fails
            verify(fileServicePort, never()).deleteFile(INPUT_BUCKET, VIDEO_KEY);

            // Status notification should report failure
            verify(videoStatusUpdatePort).notifyStatus(eq(VIDEO_KEY), eq(false), eq(0), eq(0L));

            // Error should be logged
            verify(loggerPort).error(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should handle IOException during file upload")
        void shouldHandleUploadIOException() throws IOException {
            // Arrange
            final InputStream videoStream = new ByteArrayInputStream("video".getBytes());
            final InputStream zippedFrames = new ByteArrayInputStream("zipped".getBytes());

            when(fileServicePort.getFile(INPUT_BUCKET, VIDEO_KEY))
                    .thenReturn(videoStream);
            when(videoFrameExtractorPort.extractFramesAsZip(videoStream, ENTRY_PREFIX))
                    .thenReturn(zippedFrames);
            when(fileServicePort.uploadFile(OUTPUT_BUCKET, OUTPUT_KEY, zippedFrames))
                    .thenThrow(new VideoProcessingException("Upload failed", null));

            // Act
            videoProcessorService.execute(VIDEO_KEY, INPUT_BUCKET);

            // Assert
            verify(fileServicePort, never()).deleteFile(INPUT_BUCKET, VIDEO_KEY);
            verify(videoStatusUpdatePort).notifyStatus(eq(VIDEO_KEY), eq(false), eq(0), eq(0L));
        }
    }

    @Nested
    @DisplayName("File Retrieval Failure Tests")
    class RetrievalFailureTests {

        @Test
        @DisplayName("Should handle IOException when retrieving video from S3")
        void shouldHandleFileRetrievalFailure() throws IOException {
            // Arrange
            when(fileServicePort.getFile(INPUT_BUCKET, VIDEO_KEY))
                    .thenThrow(new FileNotExistException("File not found"));

            // Act
            videoProcessorService.execute(VIDEO_KEY, INPUT_BUCKET);

            // Assert
            // Frame extraction should not occur
            verify(videoFrameExtractorPort, never()).extractFramesAsZip(any(InputStream.class), anyString());

            // Upload should not occur
            verify(fileServicePort, never()).uploadFile(anyString(), anyString(), any(InputStream.class));

            // Status notification should report failure
            verify(videoStatusUpdatePort).notifyStatus(eq(VIDEO_KEY), eq(false), eq(0), eq(0L));

            // Error should be logged
            verify(loggerPort).error(anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Status Notification Tests")
    class StatusNotificationTests {

        @Test
        @DisplayName("Should always notify status regardless of success or failure")
        void shouldAlwaysNotifyStatus() throws IOException {
            // Arrange
            when(fileServicePort.getFile(INPUT_BUCKET, VIDEO_KEY))
                    .thenThrow(new FileNotExistException("File not found"));

            // Act
            videoProcessorService.execute(VIDEO_KEY, INPUT_BUCKET);

            // Assert
            verify(videoStatusUpdatePort, times(1)).notifyStatus(anyString(), any(Boolean.class), any(Integer.class), any(Long.class));
        }

        @Test
        @DisplayName("Should notify with correct success status on successful completion")
        void shouldNotifySuccessStatus() throws IOException {
            // Arrange
            final InputStream videoStream = new ByteArrayInputStream("video".getBytes());
            final InputStream zippedFrames = new ByteArrayInputStream("zipped".getBytes());

            when(fileServicePort.getFile(INPUT_BUCKET, VIDEO_KEY))
                    .thenReturn(videoStream);
            when(videoFrameExtractorPort.extractFramesAsZip(videoStream, ENTRY_PREFIX))
                    .thenReturn(zippedFrames);
            when(fileServicePort.uploadFile(OUTPUT_BUCKET, OUTPUT_KEY, zippedFrames))
                    .thenReturn(true);
            when(fileServicePort.getSize(OUTPUT_BUCKET, OUTPUT_KEY))
                    .thenReturn(ARCHIVE_SIZE);

            // Act
            videoProcessorService.execute(VIDEO_KEY, INPUT_BUCKET);

            // Assert
            verify(videoStatusUpdatePort).notifyStatus(VIDEO_KEY, true, EXPECTED_FRAME_COUNT, ARCHIVE_SIZE);
        }

        @Test
        @DisplayName("Should notify with correct failure status and zero metrics on failure")
        void shouldNotifyFailureStatus() throws IOException {
            // Arrange
            when(fileServicePort.getFile(INPUT_BUCKET, VIDEO_KEY))
                    .thenThrow(new FileNotExistException("File not found"));

            // Act
            videoProcessorService.execute(VIDEO_KEY, INPUT_BUCKET);

            // Assert
            verify(videoStatusUpdatePort).notifyStatus(VIDEO_KEY, false, 0, 0L);
        }
    }

    @Nested
    @DisplayName("Logging Tests")
    class LoggingTests {

        @Test
        @DisplayName("Should log execution start and progress")
        void shouldLogExecutionProgress() throws IOException {
            // Arrange
            final InputStream videoStream = new ByteArrayInputStream("video".getBytes());
            final InputStream zippedFrames = new ByteArrayInputStream("zipped".getBytes());

            when(fileServicePort.getFile(INPUT_BUCKET, VIDEO_KEY))
                    .thenReturn(videoStream);
            when(videoFrameExtractorPort.extractFramesAsZip(videoStream, ENTRY_PREFIX))
                    .thenReturn(zippedFrames);
            when(fileServicePort.uploadFile(OUTPUT_BUCKET, OUTPUT_KEY, zippedFrames))
                    .thenReturn(true);
            when(fileServicePort.getSize(OUTPUT_BUCKET, OUTPUT_KEY))
                    .thenReturn(ARCHIVE_SIZE);

            // Act
            videoProcessorService.execute(VIDEO_KEY, INPUT_BUCKET);

            // Assert
            verify(loggerPort, times(1)).debug(
                    "[VideoProcessorService][execute] [Thread: {}] Starting execution, inputKey={}, inputBucket={}",
                    Thread.currentThread().getName(), VIDEO_KEY, INPUT_BUCKET);
            verify(loggerPort, times(1)).info(
                    "[VideoProcessorService][execute] [Thread: {}] Frame extraction completed, uploading to output bucket={}",
                    Thread.currentThread().getName(), OUTPUT_BUCKET);
        }

        @Test
        @DisplayName("Should log errors when exceptions occur")
        void shouldLogErrors() throws IOException {
            // Arrange
            when(fileServicePort.getFile(INPUT_BUCKET, VIDEO_KEY))
                    .thenThrow(new FileNotExistException("File not found"));

            // Act
            videoProcessorService.execute(VIDEO_KEY, INPUT_BUCKET);

            // Assert
            verify(loggerPort).error(anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle video key without extension")
        void shouldHandleVideoKeyWithoutExtension() throws IOException {
            // Arrange
            final String videoKeyNoExt = "videos/video-no-extension";
            final String expectedOutputKey = "video-processed-storage/end-process/video-no-extension.zip";
            final String expectedPrefix = "video-no-extension";

            final InputStream videoStream = new ByteArrayInputStream("video".getBytes());
            final InputStream zippedFrames = new ByteArrayInputStream("zipped".getBytes());

            when(fileServicePort.getFile(INPUT_BUCKET, videoKeyNoExt))
                    .thenReturn(videoStream);
            when(videoFrameExtractorPort.extractFramesAsZip(videoStream, expectedPrefix))
                    .thenReturn(zippedFrames);
            when(fileServicePort.uploadFile(OUTPUT_BUCKET, expectedOutputKey, zippedFrames))
                    .thenReturn(true);
            when(fileServicePort.getSize(OUTPUT_BUCKET, expectedOutputKey))
                    .thenReturn(ARCHIVE_SIZE);

            // Act
            videoProcessorService.execute(videoKeyNoExt, INPUT_BUCKET);

            // Assert
            verify(videoFrameExtractorPort).extractFramesAsZip(any(InputStream.class), eq(expectedPrefix));
            verify(fileServicePort).uploadFile(eq(OUTPUT_BUCKET), eq(expectedOutputKey), any(InputStream.class));
        }

        @Test
        @DisplayName("Should handle very large archive sizes correctly")
        void shouldHandleLargeArchiveSizes() throws IOException {
            // Arrange
            final long largeArchiveSize = 512_000_000; // 512 MB
            final int expectedLargeFrameCount = (int) (largeArchiveSize / (50 * 1024));

            final InputStream videoStream = new ByteArrayInputStream("video".getBytes());
            final InputStream zippedFrames = new ByteArrayInputStream("zipped".getBytes());

            when(fileServicePort.getFile(INPUT_BUCKET, VIDEO_KEY))
                    .thenReturn(videoStream);
            when(videoFrameExtractorPort.extractFramesAsZip(videoStream, ENTRY_PREFIX))
                    .thenReturn(zippedFrames);
            when(fileServicePort.uploadFile(OUTPUT_BUCKET, OUTPUT_KEY, zippedFrames))
                    .thenReturn(true);
            when(fileServicePort.getSize(OUTPUT_BUCKET, OUTPUT_KEY))
                    .thenReturn(largeArchiveSize);

            // Act
            videoProcessorService.execute(VIDEO_KEY, INPUT_BUCKET);

            // Assert
            verify(videoStatusUpdatePort).notifyStatus(VIDEO_KEY, true, expectedLargeFrameCount, largeArchiveSize);
        }

        @Test
        @DisplayName("Should handle minimum frame count when archive size is very small")
        void shouldHandleSmallArchiveSizes() throws IOException {
            // Arrange
            final long smallArchiveSize = 1024; // 1 KB

            final InputStream videoStream = new ByteArrayInputStream("video".getBytes());
            final InputStream zippedFrames = new ByteArrayInputStream("zipped".getBytes());

            when(fileServicePort.getFile(INPUT_BUCKET, VIDEO_KEY))
                    .thenReturn(videoStream);
            when(videoFrameExtractorPort.extractFramesAsZip(videoStream, ENTRY_PREFIX))
                    .thenReturn(zippedFrames);
            when(fileServicePort.uploadFile(OUTPUT_BUCKET, OUTPUT_KEY, zippedFrames))
                    .thenReturn(true);
            when(fileServicePort.getSize(OUTPUT_BUCKET, OUTPUT_KEY))
                    .thenReturn(smallArchiveSize);

            // Act
            videoProcessorService.execute(VIDEO_KEY, INPUT_BUCKET);

            // Assert
            final int expectedFrameCount = 0; // 1024 / (50 * 1024) = 0
            verify(videoStatusUpdatePort).notifyStatus(VIDEO_KEY, true, expectedFrameCount, smallArchiveSize);
        }
    }

}

