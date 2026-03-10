package com.hackathon.processvideo.infra.adapter.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.hackathon.processvideo.domain.exception.VideoProcessingException;
import com.hackathon.processvideo.domain.port.out.LoggerPort;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.DemuxerTrackMeta;
import org.jcodec.common.SeekableDemuxerTrack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FramesExtractorTest {
    private FramesExtractor framesExtractor;
    private LoggerPort loggerPort;
    private ManifestGenerator manifestGenerator;

    @BeforeEach
    void setUp() {
        loggerPort = mock(LoggerPort.class);
        manifestGenerator = mock(ManifestGenerator.class);
        framesExtractor = new FramesExtractor(loggerPort, 2, 1, manifestGenerator);
    }

    private InputStream loadVideoFile() throws IOException {
        InputStream stream = getClass().getResourceAsStream("/com/hackathon/processvideo/resources/video.mp4");
        if (stream == null) stream = getClass().getResourceAsStream("/video.mp4");
        if (stream != null) return stream;

        String[] possiblePaths = {
                "src/test/java/com/hackathon/processvideo/resources/video.mp4",
                "src/test/resources/com/hackathon/processvideo/resources/video.mp4",
                "src/test/resources/video.mp4"
        };

        for (String path : possiblePaths) {
            File file = new File(path);
            if (file.exists()) return Files.newInputStream(file.toPath());
        }
        throw new IOException("video.mp4 not found");
    }

    @Test
    @DisplayName("Coverage: Successful frame extraction and poison pill handling")
    void coverage_SuccessfulExtraction() throws IOException {
        InputStream videoStream = loadVideoFile();
        InputStream result = framesExtractor.extractFramesAsZip(videoStream, "frame");
        assertNotNull(result);

        // Ajustado para aceitar qualquer argumento extra do varargs (Object...)
        verify(loggerPort, timeout(10000)).debug(
                contains("Received poison pill, terminating ZIP writer"),
                any()
        );
    }

    @Test
    @DisplayName("Coverage: ZIP writer thread successfully writes frames")
    void coverage_ZipWriterFrameProcessing() throws IOException {
        InputStream videoStream = loadVideoFile();
        framesExtractor.extractFramesAsZip(videoStream, "prefix");

        verify(loggerPort, timeout(10000).atLeastOnce()).debug(
                contains("Writing frame"),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Coverage: Frame extraction task processing")
    void coverage_FrameExtractionTask() throws IOException {
        InputStream videoStream = loadVideoFile();
        framesExtractor.extractFramesAsZip(videoStream, "prefix");

        verify(loggerPort, timeout(10000).atLeastOnce()).debug(
                contains("Successfully extracted frame"),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Coverage: Resource cleanup on completion")
    void coverage_ResourceCleanup() throws IOException {
        InputStream videoStream = loadVideoFile();
        framesExtractor.extractFramesAsZip(videoStream, "prefix");

        // Removido o segundo matcher pois o log real não envia args extras aqui
        verify(loggerPort, timeout(10000).atLeastOnce()).debug(
                "[FramesExtractor][closeResourcesSafely] Closing all resources"
        );
    }

    @Test
    @DisplayName("Verify Null Input Error Log")
    void extractFramesAsZip_NullInput_ExactMatch() {
        assertThrows(VideoProcessingException.class, () ->
                framesExtractor.extractFramesAsZip(null, "prefix")
        );

        verify(loggerPort).error(
                "[FramesExtractor][extractFramesAsZip] Failed to create ZIP stream, error={}",
                "Video data stream is null"
        );
    }
}

@ExtendWith(MockitoExtension.class)
class FramesExtractorGetTotalFramesTest {

    @Mock
    private LoggerPort loggerPort;

    @Mock
    private ManifestGenerator manifestGenerator;

    private FramesExtractor framesExtractor;
    private Method getTotalFramesFromVideoMethod;

     @BeforeEach
     void setUp() throws Exception {
         framesExtractor = new FramesExtractor(loggerPort, 2, 1, manifestGenerator);

         // Use reflection to access the private method
         getTotalFramesFromVideoMethod = FramesExtractor.class.getDeclaredMethod(
                 "getTotalFramesFromVideo",
                 FrameGrab.class
         );
         getTotalFramesFromVideoMethod.setAccessible(true);
     }

    private FrameGrab createMockFrameGrab(int totalFrames, double totalDuration) {
        FrameGrab frameGrab = mock(FrameGrab.class);
        // FIX: Mock the specific child class required by the return type
        SeekableDemuxerTrack videoTrack = mock(SeekableDemuxerTrack.class);
        DemuxerTrackMeta trackMeta = mock(DemuxerTrackMeta.class);

        // No casting needed now
        lenient().when(frameGrab.getVideoTrack()).thenReturn(videoTrack);
        lenient().when(videoTrack.getMeta()).thenReturn(trackMeta);
        lenient().when(trackMeta.getTotalFrames()).thenReturn(totalFrames);
        lenient().when(trackMeta.getTotalDuration()).thenReturn(totalDuration);

        return frameGrab;
    }

    private FrameGrab createMockFrameGrabWithException(int totalFrames, RuntimeException exception) {
        FrameGrab frameGrab = mock(FrameGrab.class);
        // FIX: Mock the specific child class
        SeekableDemuxerTrack videoTrack = mock(SeekableDemuxerTrack.class);
        DemuxerTrackMeta trackMeta = mock(DemuxerTrackMeta.class);

        lenient().when(frameGrab.getVideoTrack()).thenReturn(videoTrack);
        lenient().when(videoTrack.getMeta()).thenReturn(trackMeta);
        lenient().when(trackMeta.getTotalFrames()).thenReturn(totalFrames);
        // This stubbing will now complete correctly before throwing the exception during execution
        lenient().when(trackMeta.getTotalDuration()).thenThrow(exception);

        return frameGrab;
    }

    private int invokeTotalFramesMethod(FrameGrab grab) throws Exception {
        return (int) getTotalFramesFromVideoMethod.invoke(framesExtractor, grab);
    }

    @Test
    @DisplayName("Should return frame count from metadata when totalFrames > 0")
    void getTotalFramesFromVideo_ValidMetadata_ReturnsFrameCount() throws Exception {
        // Arrange
        FrameGrab frameGrab = createMockFrameGrab(150, 0.0);

        // Act
        int result = invokeTotalFramesMethod(frameGrab);

        // Assert
        assertEquals(150, result);
        verify(loggerPort).debug("[FramesExtractor][getTotalFramesFromVideo] Retrieving total frame count from metadata");
        verify(loggerPort).debug("[FramesExtractor][getTotalFramesFromVideo] Frame count obtained from metadata, totalFrames={}", 150);
    }

    @Test
    @DisplayName("Should estimate from duration when totalFrames is 0")
    void getTotalFramesFromVideo_ZeroFrames_EstimatesFromDuration() throws Exception {
        // Arrange
        FrameGrab frameGrab = createMockFrameGrab(0, 10.0);

        // Expected: 10 seconds * 30 fps = 300 frames
        final int expectedFrames = 300;

        // Act
        int result = invokeTotalFramesMethod(frameGrab);

        // Assert
        assertEquals(expectedFrames, result);
        verify(loggerPort).debug("[FramesExtractor][getTotalFramesFromVideo] Retrieving total frame count from metadata");
        verify(loggerPort).debug("[FramesExtractor][getTotalFramesFromVideo] Frame count invalid, estimating from duration");
        verify(loggerPort).debug("[FramesExtractor][getTotalFramesFromVideo] Estimated frame count, estimatedFrames={}", expectedFrames);
    }

    @Test
    @DisplayName("Should estimate from duration when totalFrames is negative")
    void getTotalFramesFromVideo_NegativeFrames_EstimatesFromDuration() throws Exception {
        // Arrange
        FrameGrab frameGrab = createMockFrameGrab(-1, 5.5);

        // Expected: 5.5 seconds * 30 fps = 165 frames
        final int expectedFrames = 165;

        // Act
        int result = invokeTotalFramesMethod(frameGrab);

        // Assert
        assertEquals(expectedFrames, result);
        verify(loggerPort).debug("[FramesExtractor][getTotalFramesFromVideo] Frame count invalid, estimating from duration");
        verify(loggerPort).debug("[FramesExtractor][getTotalFramesFromVideo] Estimated frame count, estimatedFrames={}", expectedFrames);
    }

    @Test
    @DisplayName("Should return 1 when estimated frames would be 0")
    void getTotalFramesFromVideo_VeryShortDuration_ReturnsMinimum() throws Exception {
        // Arrange
        FrameGrab frameGrab = createMockFrameGrab(0, 0.01);

        // Expected: Math.max((int)(0.01 * 30), 1) = Math.max(0, 1) = 1

        // Act
        int result = invokeTotalFramesMethod(frameGrab);

        // Assert
        assertEquals(1, result);
        verify(loggerPort).debug("[FramesExtractor][getTotalFramesFromVideo] Frame count invalid, estimating from duration");
        verify(loggerPort).debug("[FramesExtractor][getTotalFramesFromVideo] Estimated frame count, estimatedFrames={}", 0);
    }

    @Test
    @DisplayName("Should return 1 when duration is 0")
    void getTotalFramesFromVideo_ZeroDuration_ReturnsMinimum() throws Exception {
        // Arrange
        FrameGrab frameGrab = createMockFrameGrab(0, 0.0);

        // Act
        int result = invokeTotalFramesMethod(frameGrab);

        // Assert
        assertEquals(1, result);
        verify(loggerPort).debug("[FramesExtractor][getTotalFramesFromVideo] Frame count invalid, estimating from duration");
        verify(loggerPort).warn("[FramesExtractor][getTotalFramesFromVideo] Using minimum frame count of 1");
    }

    @Test
    @DisplayName("Should return 1 when duration is negative")
    void getTotalFramesFromVideo_NegativeDuration_ReturnsMinimum() throws Exception {
        // Arrange
        FrameGrab frameGrab = createMockFrameGrab(0, -5.0);

        // Act
        int result = invokeTotalFramesMethod(frameGrab);

        // Assert
        assertEquals(1, result);
        verify(loggerPort).debug("[FramesExtractor][getTotalFramesFromVideo] Frame count invalid, estimating from duration");
        verify(loggerPort).warn("[FramesExtractor][getTotalFramesFromVideo] Using minimum frame count of 1");
    }

    @Test
    @DisplayName("Should handle ArithmeticException during estimation")
    void getTotalFramesFromVideo_ArithmeticException_ReturnsMinimum() throws Exception {
        // Arrange
        FrameGrab frameGrab = createMockFrameGrabWithException(0, new ArithmeticException("Division by zero"));

        // Act
        int result = invokeTotalFramesMethod(frameGrab);

        // Assert
        assertEquals(1, result);
        verify(loggerPort).debug("[FramesExtractor][getTotalFramesFromVideo] Retrieving total frame count from metadata");
        verify(loggerPort).debug("[FramesExtractor][getTotalFramesFromVideo] Frame count invalid, estimating from duration");
        verify(loggerPort).warn("[FramesExtractor][getTotalFramesFromVideo] Could not estimate from duration, error={}", "Division by zero");
        verify(loggerPort).warn("[FramesExtractor][getTotalFramesFromVideo] Using minimum frame count of 1");
    }

    @Test
    @DisplayName("Should handle NumberFormatException during estimation")
    void getTotalFramesFromVideo_NumberFormatException_ReturnsMinimum() throws Exception {
        // Arrange
        FrameGrab frameGrab = createMockFrameGrabWithException(0, new NumberFormatException("Invalid number"));

        // Act
        int result = invokeTotalFramesMethod(frameGrab);

        // Assert
        assertEquals(1, result);
        verify(loggerPort).warn("[FramesExtractor][getTotalFramesFromVideo] Could not estimate from duration, error={}", "Invalid number");
        verify(loggerPort).warn("[FramesExtractor][getTotalFramesFromVideo] Using minimum frame count of 1");
    }

    @Test
    @DisplayName("Should return exactly 1 frame for minimum viable video")
    void getTotalFramesFromVideo_MinimumViableVideo_ReturnsOne() throws Exception {
        // Arrange
        FrameGrab frameGrab = createMockFrameGrab(1, 0.0);

        // Act
        int result = invokeTotalFramesMethod(frameGrab);

        // Assert
        assertEquals(1, result);
        verify(loggerPort).debug("[FramesExtractor][getTotalFramesFromVideo] Frame count obtained from metadata, totalFrames={}", 1);
    }

    @Test
    @DisplayName("Should handle large frame counts correctly")
    void getTotalFramesFromVideo_LargeFrameCount_ReturnsCorrectValue() throws Exception {
        // Arrange
        final int largeFrameCount = 100000; // ~55 minutes at 30fps
        FrameGrab frameGrab = createMockFrameGrab(largeFrameCount, 0.0);

        // Act
        int result = invokeTotalFramesMethod(frameGrab);

        // Assert
        assertEquals(largeFrameCount, result);
        verify(loggerPort).debug("[FramesExtractor][getTotalFramesFromVideo] Frame count obtained from metadata, totalFrames={}", largeFrameCount);
    }

    @Test
    @DisplayName("Should handle fractional duration correctly")
    void getTotalFramesFromVideo_FractionalDuration_RoundsDown() throws Exception {
        // Arrange
        FrameGrab frameGrab = createMockFrameGrab(0, 3.7);

        // Expected: (int)(3.7 * 30) = (int)(111.0) = 111 frames

        // Act
        int result = invokeTotalFramesMethod(frameGrab);

        // Assert
        assertEquals(111, result);
        verify(loggerPort).debug("[FramesExtractor][getTotalFramesFromVideo] Estimated frame count, estimatedFrames={}", 111);
    }

    @Test
    @DisplayName("Should verify all log calls in successful metadata path")
    void getTotalFramesFromVideo_SuccessPath_LogsCorrectly() throws Exception {
        // Arrange
        FrameGrab frameGrab = createMockFrameGrab(90, 0.0);

        // Act
        int result = invokeTotalFramesMethod(frameGrab);

        // Assert
        assertEquals(90, result);

        // Verify exact log sequence
        verify(loggerPort).debug("[FramesExtractor][getTotalFramesFromVideo] Retrieving total frame count from metadata");
        verify(loggerPort).debug("[FramesExtractor][getTotalFramesFromVideo] Frame count obtained from metadata, totalFrames={}", 90);
    }

    @Test
    @DisplayName("Should verify all log calls in estimation fallback path")
    void getTotalFramesFromVideo_EstimationPath_LogsCorrectly() throws Exception {
        // Arrange
        FrameGrab frameGrab = createMockFrameGrab(-10, 2.0);

        // Act
        int result = invokeTotalFramesMethod(frameGrab);

        // Assert
        assertEquals(60, result);

        // Verify exact log sequence
        verify(loggerPort).debug("[FramesExtractor][getTotalFramesFromVideo] Retrieving total frame count from metadata");
        verify(loggerPort).debug("[FramesExtractor][getTotalFramesFromVideo] Frame count invalid, estimating from duration");
        verify(loggerPort).debug("[FramesExtractor][getTotalFramesFromVideo] Estimated frame count, estimatedFrames={}", 60);
    }

    @Test
    @DisplayName("Should verify all log calls in complete fallback path")
    void getTotalFramesFromVideo_CompleteFallback_LogsCorrectly() throws Exception {
        // Arrange
        FrameGrab frameGrab = createMockFrameGrab(0, 0.0);

        // Act
        int result = invokeTotalFramesMethod(frameGrab);

        // Assert
        assertEquals(1, result);

        // Verify exact log sequence
        verify(loggerPort).debug("[FramesExtractor][getTotalFramesFromVideo] Retrieving total frame count from metadata");
        verify(loggerPort).debug("[FramesExtractor][getTotalFramesFromVideo] Frame count invalid, estimating from duration");
        verify(loggerPort).warn("[FramesExtractor][getTotalFramesFromVideo] Using minimum frame count of 1");
    }
}
