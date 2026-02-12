package com.hackathon.processvideo.infra.adapter.outbound;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hackathon.processvideo.domain.port.out.LoggerPort;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PipedOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FramesExtractorPrivateMethodsTest {

    @Mock
    private LoggerPort loggerPort;


    private FramesExtractor framesExtractor;

    @BeforeEach
    void setUp() {
        framesExtractor = new FramesExtractor(loggerPort, 2, 10);
    }

    @Test
    @DisplayName("createSecureDirectory: Should create directory with restricted permissions")
    void createSecureDirectory_Success(@TempDir Path tempDir) throws Exception {
        Path targetDir = tempDir.resolve("secure-dir");
        Method method = FramesExtractor.class.getDeclaredMethod("createSecureDirectory", Path.class);
        method.setAccessible(true);

        method.invoke(framesExtractor, targetDir);

        assertTrue(Files.exists(targetDir));

        // Check OS to decide which log message to verify
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            verify(loggerPort, atLeastOnce()).debug(Mockito.contains("Applied directory permissions"), any());
        } else {
            // On Linux/Mac or environments supporting POSIX
            // However, @TempDir usually supports POSIX attributes if file system does.
            // If test runs in Docker/Linux, this might be called.
            // To be safe, we can try verify one OR the other, but Mockito doesn't have "OR"
            // easily without verification collector.
            // Given User is on Windows, we prioritize that path.
            // If we want to be robust:
            try {
                verify(loggerPort, atLeastOnce()).debug(Mockito.contains("Created secure temp directory"), any());
            } catch (AssertionError e) {
                verify(loggerPort, atLeastOnce()).debug(Mockito.contains("Applied directory permissions"));
            }
        }

        File file = targetDir.toFile();
        // Readable/Writable checks are generally true for owner on newly created dirs
        assertTrue(file.canRead());
        assertTrue(file.canWrite());
    }

    @Test
    @DisplayName("createSecureDirectory: Should handle existing directory")
    void createSecureDirectory_AlreadyExists(@TempDir Path tempDir) throws Exception {
        Path targetDir = tempDir.resolve("existing-dir");
        Files.createDirectories(targetDir);

        Method method = FramesExtractor.class.getDeclaredMethod("createSecureDirectory", Path.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(framesExtractor, targetDir));
    }

    @Test
    @DisplayName("createSecureTempFile: Should create file in default temp dir if property not set")
    void createSecureTempFile_DefaultDir() throws Exception {
        System.clearProperty("app.temp.dir");

        Method method = FramesExtractor.class.getDeclaredMethod("createSecureTempFile", String.class, String.class);
        method.setAccessible(true);

        File tempFile = (File) method.invoke(framesExtractor, "prefix_", ".tmp");

        assertNotNull(tempFile);
        assertTrue(tempFile.exists());
        assertTrue(tempFile.getName().startsWith("prefix_"));
        assertTrue(tempFile.getName().endsWith(".tmp"));

        tempFile.delete();
    }

    @Test
    @DisplayName("createSecureTempFile: Should create file in custom temp dir if property set")
    void createSecureTempFile_CustomDir(@TempDir Path customTempDir) throws Exception {
        System.setProperty("app.temp.dir", customTempDir.toAbsolutePath().toString());

        Method method = FramesExtractor.class.getDeclaredMethod("createSecureTempFile", String.class, String.class);
        method.setAccessible(true);

        File tempFile = (File) method.invoke(framesExtractor, "custom_", ".tmp");

        assertNotNull(tempFile);
        assertTrue(tempFile.exists());

        // Canonical paths might differ on Windows (long vs short), but parent should
        // match
        assertEquals(customTempDir.toFile().getCanonicalPath(), tempFile.getParentFile().getCanonicalPath());

        System.clearProperty("app.temp.dir");
    }

    @Test
    @DisplayName("extractFrameTask: Should handle failure with invalid file")
    void extractFrameTask_Failure() throws Exception {
        Method method = FramesExtractor.class.getDeclaredMethod("extractFrameTask", File.class, int.class, int.class);
        method.setAccessible(true);

        File dummyFile = Files.createTempFile("dummy", ".mp4").toFile();

        // This should return an ExtractedFrame with error because dummy file is empty
        Object result = method.invoke(framesExtractor, dummyFile, 0, 0);

        assertNotNull(result);
        Method isErrorMethod = result.getClass().getDeclaredMethod("isError");
        isErrorMethod.setAccessible(true);
        boolean isError = (boolean) isErrorMethod.invoke(result);
        assertTrue(isError, "Should be error for invalid video file");

        dummyFile.delete();
    }

    @Test
    @DisplayName("submitFrameExtractionTasks: Should submit tasks to queue even if extraction fails")
    void submitFrameExtractionTasks_Success() throws Exception {
        BlockingQueue<Object> queue = new LinkedBlockingQueue<>();

        Method method = FramesExtractor.class.getDeclaredMethod("submitFrameExtractionTasks", File.class, int.class,
                BlockingQueue.class);
        method.setAccessible(true);

        File tempFile = Files.createTempFile("video", ".mp4").toFile();

        // 60 FPS constant, so for 120 frames total, it acts.
        method.invoke(framesExtractor, tempFile, 120, queue);

        // Expect: 120 frames / 60 FPS = 2 tasks submitted
        assertEquals(2, queue.size());

        tempFile.delete();
    }

    @Test
    @DisplayName("writeFramesToZip: Should write frames to zip")
    void writeFramesToZip_Success() throws Exception {
        BlockingQueue<Object> queue = new LinkedBlockingQueue<>();

        // Create ExtractedFrame
        Class<?> extractedFrameClass = Class
                .forName("com.hackathon.processvideo.infra.adapter.outbound.FramesExtractor$ExtractedFrame");
        Constructor<?> constructor = extractedFrameClass.getDeclaredConstructor(int.class, BufferedImage.class);
        constructor.setAccessible(true);

        BufferedImage dummyImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Object frame = constructor.newInstance(1, dummyImage);

        queue.put(frame);

        // Add Poison Pill
        Field field = FramesExtractor.class.getDeclaredField("POISON_PILL");
        field.setAccessible(true);
        Object poisonPill = field.get(null);
        queue.put(poisonPill);

        ZipOutputStream zos = mock(ZipOutputStream.class);

        // Create ZipStreamWithLock
        Class<?> lockClass = Class
                .forName("com.hackathon.processvideo.infra.adapter.outbound.FramesExtractor$ZipStreamWithLock");
        Constructor<?> lockConstructor = lockClass.getDeclaredConstructor(ZipOutputStream.class);
        lockConstructor.setAccessible(true);
        Object lock = lockConstructor.newInstance(zos);

        Method method = FramesExtractor.class.getDeclaredMethod("writeFramesToZip", BlockingQueue.class, lockClass);
        method.setAccessible(true);

        method.invoke(framesExtractor, queue, lock);

        verify(zos, atLeastOnce()).putNextEntry(any());
        verify(zos, atLeastOnce()).write(any(byte[].class));
    }

    @Test
    @DisplayName("extractFramesInBackground: Should handle exceptions and log error")
    void extractFramesInBackground_Exception() throws Exception {
        File dummyFile = Files.createTempFile("bad_video", ".mp4").toFile();
        // Write garbage to ensure JCodec throws exception
        Files.writeString(dummyFile.toPath(), "Garbage video content");

        PipedOutputStream pos = new PipedOutputStream();

        Method method = FramesExtractor.class.getDeclaredMethod("extractFramesInBackground", File.class,
                PipedOutputStream.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(framesExtractor, dummyFile, pos));

        // Verify error() was called at least once during exception handling
        // We use atLeastOnce() because there may be multiple error() calls with different numbers of arguments
        verify(loggerPort, atLeastOnce()).error(any(String.class), any());

        dummyFile.delete();
    }
}
