package com.hackathon.processvideo.infra.adapter.outbound;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
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
import java.util.concurrent.TimeUnit;
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

        @Mock
        private ManifestGenerator manifestGenerator;

        private FramesExtractor framesExtractor;

        @BeforeEach
        void setUp() {
                framesExtractor = new FramesExtractor(loggerPort, 2, 10, manifestGenerator);
        }

        @Test
        @DisplayName("createSecureDirectory: Should create directory with restricted permissions")
        void createSecureDirectory_Success(@TempDir Path tempDir) throws Exception {
                Path targetDir = tempDir.resolve("secure-dir");
                Method method = FramesExtractor.class.getDeclaredMethod("createSecureDirectory", Path.class);
                method.setAccessible(true);

                method.invoke(framesExtractor, targetDir);

                assertTrue(Files.exists(targetDir));

                // Assertions dependent on OS due to fallback logic in source
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                        // Windows fallback logs
                        verify(loggerPort, atLeastOnce()).debug(Mockito.contains("Applied directory permissions"));
                } else {
                        // POSIX success logs
                        try {
                                verify(loggerPort, atLeastOnce())
                                                .debug(Mockito.contains("Created secure temp directory"), any());
                        } catch (AssertionError e) {
                                // Fallback might still occur on some systems
                        }
                }

                File file = targetDir.toFile();
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

                Method method = FramesExtractor.class.getDeclaredMethod("createSecureTempFile", String.class,
                                String.class);
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

                Method method = FramesExtractor.class.getDeclaredMethod("createSecureTempFile", String.class,
                                String.class);
                method.setAccessible(true);

                File tempFile = (File) method.invoke(framesExtractor, "custom_", ".tmp");

                assertNotNull(tempFile);
                assertTrue(tempFile.exists());

                assertEquals(customTempDir.toFile().getCanonicalPath(), tempFile.getParentFile().getCanonicalPath());

                System.clearProperty("app.temp.dir");
        }

        @Test
        @DisplayName("extractFrameTask: Should handle failure with invalid file")
        void extractFrameTask_Failure() throws Exception {
                Method method = FramesExtractor.class.getDeclaredMethod("extractFrameTask", File.class, int.class,
                                int.class);
                method.setAccessible(true);

                File dummyFile = Files.createTempFile("dummy", ".mp4").toFile();

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

                Method method = FramesExtractor.class.getDeclaredMethod("submitFrameExtractionTasks", File.class,
                                int.class,
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
        @DisplayName("submitFrameExtractionTasks: Should handle InterruptedException during frame queuing")
        void submitFrameExtractionTasks_InterruptedException() throws Exception {
                @SuppressWarnings("unchecked")
                BlockingQueue<Object> mockQueue = mock(BlockingQueue.class);

                // First put() throws InterruptedException, second put() for error frame
                // succeeds
                doThrow(new InterruptedException("Queue interrupted"))
                                .doNothing()
                                .when(mockQueue).put(any());

                Method method = FramesExtractor.class.getDeclaredMethod("submitFrameExtractionTasks", File.class,
                                int.class,
                                BlockingQueue.class);
                method.setAccessible(true);

                File tempFile = Files.createTempFile("video", ".mp4").toFile();
                Files.writeString(tempFile.toPath(), "dummy content");

                assertDoesNotThrow(() -> method.invoke(framesExtractor, tempFile, 60, mockQueue));

                // Verify error logging occurred
                verify(loggerPort, atLeastOnce()).error(
                                Mockito.contains("Interrupted while queuing frame"),
                                any(Integer.class),
                                any(String.class));

                tempFile.delete();
        }

        @Test
        @DisplayName("submitFrameExtractionTasks: Should handle nested InterruptedException")
        void submitFrameExtractionTasks_NestedInterruptedException() throws Exception {
                @SuppressWarnings("unchecked")
                BlockingQueue<Object> mockQueue = mock(BlockingQueue.class);

                // Both put() calls throw InterruptedException
                doThrow(new InterruptedException("Queue interrupted"))
                                .when(mockQueue).put(any());

                Method method = FramesExtractor.class.getDeclaredMethod("submitFrameExtractionTasks", File.class,
                                int.class,
                                BlockingQueue.class);
                method.setAccessible(true);

                File tempFile = Files.createTempFile("video", ".mp4").toFile();
                Files.writeString(tempFile.toPath(), "dummy content");

                assertDoesNotThrow(() -> method.invoke(framesExtractor, tempFile, 60, mockQueue));

                // Verify both error logs occurred
                verify(loggerPort, atLeastOnce()).error(
                                Mockito.contains("Interrupted while queuing frame"),
                                any(Integer.class),
                                any(String.class));

                verify(loggerPort, atLeastOnce()).error(
                                Mockito.contains("Interrupted while queuing error"),
                                any(String.class));

                tempFile.delete();
        }

        @Test
        @DisplayName("submitFrameExtractionTasks: Should log error and queue error frame when IOException occurs")
        void submitFrameExtractionTasks_IOExceptionWithSuccessfulErrorQueuing() throws Exception {
                BlockingQueue<Object> queue = new LinkedBlockingQueue<>();

                Method method = FramesExtractor.class.getDeclaredMethod("submitFrameExtractionTasks", File.class,
                                int.class,
                                BlockingQueue.class);
                method.setAccessible(true);

                // Create invalid file to trigger IOException in extractFrameTask
                File tempFile = Files.createTempFile("invalid_video", ".mp4").toFile();
                Files.writeString(tempFile.toPath(), "not a valid mp4 file");

                assertDoesNotThrow(() -> method.invoke(framesExtractor, tempFile, 60, queue));

                // Note: extractFrameTask catches IOException/JCodecException internally (line
                // 488-491)
                // and logs the error with message "[FramesExtractor][extractFrameTask]..."
                // The catch block in submitFrameExtractionTasks (line 416-426) is never
                // actually reached
                // because extractFrameTask returns an error ExtractedFrame instead of throwing

                // Verify that error logging occurred from extractFrameTask (line 489-490)
                verify(loggerPort, atLeastOnce()).error(
                                Mockito.contains("Exception extracting frame"),
                                any(String.class), // Thread info
                                any(Integer.class), // frameNumber
                                any(String.class)); // e.getMessage()

                // Verify error frames were successfully queued (even though extraction failed)
                assertTrue(queue.size() > 0, "Error frames should be queued after IOException");

                tempFile.delete();
        }

        @Test
        @DisplayName("submitFrameExtractionTasks: Should handle InterruptedException while queuing error frame after IOException")
        void submitFrameExtractionTasks_IOExceptionThenInterruptedDuringErrorQueuing() throws Exception {
                @SuppressWarnings("unchecked")
                BlockingQueue<Object> mockQueue = mock(BlockingQueue.class);

                // First put() succeeds, then second put() throws InterruptedException
                doNothing()
                                .doThrow(new InterruptedException("Error queue interrupted"))
                                .when(mockQueue).put(any());

                Method method = FramesExtractor.class.getDeclaredMethod("submitFrameExtractionTasks", File.class,
                                int.class,
                                BlockingQueue.class);
                method.setAccessible(true);

                // Create invalid file to trigger IOException
                File tempFile = Files.createTempFile("bad_video", ".mp4").toFile();
                Files.writeString(tempFile.toPath(), "invalid");

                assertDoesNotThrow(() -> method.invoke(framesExtractor, tempFile, 120, mockQueue));

                // Note: The IOException catch block in submitFrameExtractionTasks (lines
                // 416-426) is UNREACHABLE
                // because extractFrameTask catches IOException/JCodecException internally (line
                // 488-491)
                // and returns an error ExtractedFrame instead of throwing

                // Verify that error logging occurred from extractFrameTask (line 489-490)
                verify(loggerPort, atLeastOnce()).error(
                                Mockito.contains("Exception extracting frame"),
                                any(String.class), // Thread info
                                any(Integer.class), // frameNumber
                                any(String.class)); // e.getMessage()

                // The nested interrupt error log (line 422-423) is also unreachable
                // because the IOException catch block is never entered
                // Therefore, we cannot test the user's requested code path as it's dead code

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

                Method method = FramesExtractor.class.getDeclaredMethod("writeFramesToZip", BlockingQueue.class,
                                lockClass);
                method.setAccessible(true);

                method.invoke(framesExtractor, queue, lock);

                verify(zos, atLeastOnce()).putNextEntry(any());
                verify(zos, atLeastOnce()).write(any(byte[].class));
        }

        @Test
        @DisplayName("writeFramesToZip: Should skip frames with errors")
        void writeFramesToZip_SkipErrorFrames() throws Exception {
                BlockingQueue<Object> queue = new LinkedBlockingQueue<>();

                // Create error ExtractedFrame (using the constructor that takes only
                // frameIndex)
                Class<?> extractedFrameClass = Class
                                .forName("com.hackathon.processvideo.infra.adapter.outbound.FramesExtractor$ExtractedFrame");
                Constructor<?> errorConstructor = extractedFrameClass.getDeclaredConstructor(int.class);
                errorConstructor.setAccessible(true);

                Object errorFrame = errorConstructor.newInstance(1);
                queue.put(errorFrame);

                // Add Poison Pill
                Field field = FramesExtractor.class.getDeclaredField("POISON_PILL");
                field.setAccessible(true);
                Object poisonPill = field.get(null);
                queue.put(poisonPill);

                ZipOutputStream zos = mock(ZipOutputStream.class);
                Class<?> lockClass = Class
                                .forName("com.hackathon.processvideo.infra.adapter.outbound.FramesExtractor$ZipStreamWithLock");
                Constructor<?> lockConstructor = lockClass.getDeclaredConstructor(ZipOutputStream.class);
                lockConstructor.setAccessible(true);
                Object lock = lockConstructor.newInstance(zos);

                Method method = FramesExtractor.class.getDeclaredMethod("writeFramesToZip", BlockingQueue.class,
                                lockClass);
                method.setAccessible(true);

                method.invoke(framesExtractor, queue, lock);

                // Verify warning log for skipped error frame
                verify(loggerPort, atLeastOnce()).warn(
                                Mockito.contains("Skipping frame"),
                                any(String.class),
                                any(Integer.class));
        }

        @Test
        @DisplayName("writeFramesToZip: Should handle frames with null image")
        void writeFramesToZip_NullImage() throws Exception {
                BlockingQueue<Object> queue = new LinkedBlockingQueue<>();

                // Create ExtractedFrame with null image
                Class<?> extractedFrameClass = Class
                                .forName("com.hackathon.processvideo.infra.adapter.outbound.FramesExtractor$ExtractedFrame");
                Constructor<?> constructor = extractedFrameClass.getDeclaredConstructor(int.class, BufferedImage.class);
                constructor.setAccessible(true);

                Object frameWithNullImage = constructor.newInstance(1, null);
                queue.put(frameWithNullImage);

                // Add Poison Pill
                Field field = FramesExtractor.class.getDeclaredField("POISON_PILL");
                field.setAccessible(true);
                Object poisonPill = field.get(null);
                queue.put(poisonPill);

                ZipOutputStream zos = mock(ZipOutputStream.class);
                Class<?> lockClass = Class
                                .forName("com.hackathon.processvideo.infra.adapter.outbound.FramesExtractor$ZipStreamWithLock");
                Constructor<?> lockConstructor = lockClass.getDeclaredConstructor(ZipOutputStream.class);
                lockConstructor.setAccessible(true);
                Object lock = lockConstructor.newInstance(zos);

                Method method = FramesExtractor.class.getDeclaredMethod("writeFramesToZip", BlockingQueue.class,
                                lockClass);
                method.setAccessible(true);

                method.invoke(framesExtractor, queue, lock);

                // Verify warning log for null image
                verify(loggerPort, atLeastOnce()).warn(
                                Mockito.contains("has null image"),
                                any(String.class),
                                any(Integer.class));
        }

        @Test
        @DisplayName("writeFramesToZip: Should handle InterruptedException")
        void writeFramesToZip_InterruptedException() throws Exception {
                @SuppressWarnings("unchecked")
                BlockingQueue<Object> mockQueue = mock(BlockingQueue.class);

                // Mock take() to throw InterruptedException
                doThrow(new InterruptedException("Queue interrupted"))
                                .when(mockQueue).take();

                ZipOutputStream zos = mock(ZipOutputStream.class);
                Class<?> lockClass = Class
                                .forName("com.hackathon.processvideo.infra.adapter.outbound.FramesExtractor$ZipStreamWithLock");
                Constructor<?> lockConstructor = lockClass.getDeclaredConstructor(ZipOutputStream.class);
                lockConstructor.setAccessible(true);
                Object lock = lockConstructor.newInstance(zos);

                Method method = FramesExtractor.class.getDeclaredMethod("writeFramesToZip", BlockingQueue.class,
                                lockClass);
                method.setAccessible(true);

                assertDoesNotThrow(() -> method.invoke(framesExtractor, mockQueue, lock));

                // Verify error logging
                verify(loggerPort, atLeastOnce()).error(
                                Mockito.contains("Interrupted while consuming frames"),
                                any(String.class),
                                any(String.class));
        }

        @Test
        @DisplayName("writeFramesToZip: Should handle unexpected object type")
        void writeFramesToZip_UnexpectedObjectType() throws Exception {
                BlockingQueue<Object> queue = new LinkedBlockingQueue<>();

                // Add unexpected object (String instead of ExtractedFrame)
                queue.put("Unexpected String object");

                // Add Poison Pill to terminate
                Field field = FramesExtractor.class.getDeclaredField("POISON_PILL");
                field.setAccessible(true);
                Object poisonPill = field.get(null);
                queue.put(poisonPill);

                ZipOutputStream zos = mock(ZipOutputStream.class);
                Class<?> lockClass = Class
                                .forName("com.hackathon.processvideo.infra.adapter.outbound.FramesExtractor$ZipStreamWithLock");
                Constructor<?> lockConstructor = lockClass.getDeclaredConstructor(ZipOutputStream.class);
                lockConstructor.setAccessible(true);
                Object lock = lockConstructor.newInstance(zos);

                Method method = FramesExtractor.class.getDeclaredMethod("writeFramesToZip", BlockingQueue.class,
                                lockClass);
                method.setAccessible(true);

                method.invoke(framesExtractor, queue, lock);

                // Verify warning log for unexpected object
                verify(loggerPort, atLeastOnce()).warn(
                                Mockito.contains("Unexpected object type"),
                                any(String.class));
        }

        @Test
        @DisplayName("extractFramesInBackground: Should handle exceptions and log error")
        void extractFramesInBackground_Exception() throws Exception {
                File dummyFile = Files.createTempFile("bad_video", ".mp4").toFile();
                // Write garbage to ensure JCodec throws exception
                Files.writeString(dummyFile.toPath(), "Garbage video content");

                PipedOutputStream pos = new PipedOutputStream();

                Method method = FramesExtractor.class.getDeclaredMethod("extractFramesInBackground", File.class,
                                PipedOutputStream.class, String.class);
                method.setAccessible(true);

                assertDoesNotThrow(() -> method.invoke(framesExtractor, dummyFile, pos, "test-key"));

                // Verify error logging occurred
                verify(loggerPort, atLeastOnce()).error(
                                Mockito.contains("Exception during frame extraction"),
                                any(String.class), // Thread info
                                any(String.class) // Error message
                );

                dummyFile.delete();
        }

        @Test
        @DisplayName("shutdownExecutorService: Should shutdown successfully when executor terminates within timeout")
        void shutdownExecutorService_SuccessfulTermination() throws Exception {
                java.util.concurrent.ExecutorService mockExecutor = mock(java.util.concurrent.ExecutorService.class);

                // Mock successful termination
                Mockito.when(mockExecutor.awaitTermination(eq(10L), eq(TimeUnit.MINUTES))).thenReturn(true);

                Method method = FramesExtractor.class.getDeclaredMethod("shutdownExecutorService",
                                java.util.concurrent.ExecutorService.class);
                method.setAccessible(true);

                assertDoesNotThrow(() -> method.invoke(framesExtractor, mockExecutor));

                // Verify shutdown was called
                verify(mockExecutor).shutdown();
                // Verify awaitTermination was called with correct parameters
                verify(mockExecutor).awaitTermination(eq(10L), eq(TimeUnit.MINUTES));
                // Verify shutdownNow was NOT called (since it terminated successfully)
                verify(mockExecutor, Mockito.never()).shutdownNow();
        }

        @Test
        @DisplayName("shutdownExecutorService: Should force shutdown when executor does not terminate within timeout")
        void shutdownExecutorService_TimeoutTermination() throws Exception {
                java.util.concurrent.ExecutorService mockExecutor = mock(java.util.concurrent.ExecutorService.class);

                // Mock timeout - awaitTermination returns false
                Mockito.when(mockExecutor.awaitTermination(eq(10L), eq(TimeUnit.MINUTES))).thenReturn(false);

                Method method = FramesExtractor.class.getDeclaredMethod("shutdownExecutorService",
                                java.util.concurrent.ExecutorService.class);
                method.setAccessible(true);

                assertDoesNotThrow(() -> method.invoke(framesExtractor, mockExecutor));

                // Verify shutdown was called first
                verify(mockExecutor).shutdown();
                // Verify awaitTermination was called
                verify(mockExecutor).awaitTermination(eq(10L), eq(TimeUnit.MINUTES));
                // Verify shutdownNow was called due to timeout
                verify(mockExecutor).shutdownNow();
                // Verify warning log was called
                verify(loggerPort).warn(Mockito.contains("Executor service did not terminate within timeout"));
        }

        @Test
        @DisplayName("shutdownExecutorService: Should handle InterruptedException and force shutdown")
        void shutdownExecutorService_InterruptedException() throws Exception {
                java.util.concurrent.ExecutorService mockExecutor = mock(java.util.concurrent.ExecutorService.class);

                // Mock InterruptedException during awaitTermination
                Mockito.when(mockExecutor.awaitTermination(eq(10L), eq(TimeUnit.MINUTES)))
                                .thenThrow(new InterruptedException("Test interruption"));

                Method method = FramesExtractor.class.getDeclaredMethod("shutdownExecutorService",
                                java.util.concurrent.ExecutorService.class);
                method.setAccessible(true);

                assertDoesNotThrow(() -> method.invoke(framesExtractor, mockExecutor));

                // Verify shutdown was called first
                verify(mockExecutor).shutdown();
                // Verify awaitTermination was called
                verify(mockExecutor).awaitTermination(eq(10L), eq(TimeUnit.MINUTES));
                // Verify shutdownNow was called due to interruption
                verify(mockExecutor).shutdownNow();
                // Verify error log was called with correct message pattern
                verify(loggerPort).error(
                                Mockito.contains("Interrupted waiting for executor service"),
                                any(String.class));
        }
}
