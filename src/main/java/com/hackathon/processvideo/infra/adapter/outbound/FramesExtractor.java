package com.hackathon.processvideo.infra.adapter.outbound;

import com.hackathon.processvideo.domain.exception.VideoProcessingException;
import com.hackathon.processvideo.domain.port.out.LoggerPort;
import com.hackathon.processvideo.domain.port.out.VideoFrameExtractorPort;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FramesExtractor implements VideoFrameExtractorPort {

    private static final String IMAGE_FORMAT = "jpg";
    private static final String JPG_FORMAT = "jpg";
    private static final int JPG_QUALITY = 85; // 0-100, 85-90 recommended for optimal quality/size tradeoff
    private static final int BUFFER_SIZE = 128 * 1024;
    private static final String THREAD_NAME_PREFIX = "video-frame-extractor";
    private static final int FPS = 60; // One frame every 2 seconds (30 FPS * 2) = 50% of seconds extracted
    private static final int FRAME_RATE_ESTIMATE = 30; // Used to estimate total frames from duration
    private static final int QUALITY_SCALE = 100; // Scale factor for quality conversion
    private static final int TERMINATION_TIMEOUT_MINUTES = 10;
    private static final Object POISON_PILL = new Object(); // Sentinel value to signal queue end

    private final LoggerPort loggerPort;
    private final int threadPoolSize;
    private final int queueCapacity;

    public FramesExtractor(LoggerPort loggerPort,
                           @Value("${app.frame-extraction.thread-pool-size:4}") int threadPoolSize,
                           @Value("${app.frame-extraction.queue-capacity:100}") int queueCapacity) {
        this.loggerPort = loggerPort;
        this.threadPoolSize = threadPoolSize;
        this.queueCapacity = queueCapacity;
    }

    @Override
    public InputStream extractFramesAsZip(InputStream rawVideoData, String entryNamePrefix) {
        try {
            if (rawVideoData == null) {
                throw new IOException("Video data stream is null");
            }
            loggerPort.debug("[FramesExtractor][extractFramesAsZip] Creating temporary video file");
            final File tempVideo = createSecureTempFile("streaming_extract_", ".mp4");
            try (FileOutputStream fos = new FileOutputStream(tempVideo)) {
                rawVideoData.transferTo(fos);
            }
            loggerPort.debug("[FramesExtractor][extractFramesAsZip] Temporary video created, path={}", tempVideo.getAbsolutePath());

            final PipedOutputStream pos = new PipedOutputStream();
            final PipedInputStream pis = new PipedInputStream(pos, BUFFER_SIZE);

            final Thread worker = new Thread(() -> extractFramesInBackground(tempVideo, pos),
                    THREAD_NAME_PREFIX + "-" + System.currentTimeMillis());

            worker.setDaemon(true);
            worker.start();
            loggerPort.debug("[FramesExtractor][extractFramesAsZip] Background worker thread started");

            return pis;

        } catch (IOException e) {
            loggerPort.error("[FramesExtractor][extractFramesAsZip] Failed to create ZIP stream, error={}", e.getMessage());
            throw new VideoProcessingException("Erro ao criar stream zipada de frames", e);
        }
    }

    private void extractFramesInBackground(File tempVideo, PipedOutputStream pos) {
        SeekableByteChannel ch = null;
        ZipOutputStream zos = null;

        try {
            loggerPort.debug("[FramesExtractor][extractFramesInBackground] {} Opening video file channel", getThreadInfo());
            ch = NIOUtils.readableChannel(tempVideo);
            zos = new ZipOutputStream(pos);

            loggerPort.debug("[FramesExtractor][extractFramesInBackground] {} Creating frame grabber", getThreadInfo());
            final FrameGrab grab = FrameGrab.createFrameGrab(ch);
            final int totalFrames = getTotalFramesFromVideo(grab);

            if (totalFrames <= 0) {
                loggerPort.error("[FramesExtractor][extractFramesInBackground] {} No valid frames found in video", getThreadInfo());
                throw new VideoProcessingException("Video has no valid frames", null);
            }

            loggerPort.info(
                    "[FramesExtractor][extractFramesInBackground] {} Starting frame extraction, "
                            + "totalFrames={}, threadPoolSize={}, queueCapacity={}, imageFormat={}@{}%, "
                            + "samplingRate=50%(1frame/2sec)",
                    getThreadInfo(), totalFrames, threadPoolSize, queueCapacity, IMAGE_FORMAT, JPG_QUALITY);
            extractFramesWithMultiThread(tempVideo, totalFrames, zos);

            loggerPort.debug("[FramesExtractor][extractFramesInBackground] {} Finishing ZIP output stream", getThreadInfo());
            zos.finish();
            loggerPort.info("[FramesExtractor][extractFramesInBackground] {} Frame extraction completed successfully", getThreadInfo());

        } catch (IOException | JCodecException e) {
            loggerPort
                    .error("[FramesExtractor][extractFramesInBackground] {} Exception during frame extraction, error={}", getThreadInfo(),
                            e.getMessage());
            handleExtractionError(e, pos);
        } finally {
            closeResourcesSafely(ch, zos, pos, tempVideo);
        }
    }

    private int getTotalFramesFromVideo(FrameGrab grab) {
        loggerPort.debug("[FramesExtractor][getTotalFramesFromVideo] Retrieving total frame count from metadata");
        final int totalFrames = grab.getVideoTrack().getMeta().getTotalFrames();

        if (totalFrames > 0) {
            loggerPort.debug("[FramesExtractor][getTotalFramesFromVideo] Frame count obtained from metadata, totalFrames={}", totalFrames);
            return totalFrames;
        }

        try {
            loggerPort.debug("[FramesExtractor][getTotalFramesFromVideo] Frame count invalid, "
                    + "estimating from duration");
            final double totalDuration = grab.getVideoTrack().getMeta().getTotalDuration();
            if (totalDuration > 0) {
                final int estimatedFrames = (int) (totalDuration * FRAME_RATE_ESTIMATE);
                loggerPort.debug(
                        "[FramesExtractor][getTotalFramesFromVideo] Estimated frame count, "
                                + "estimatedFrames={}", estimatedFrames);
                return Math.max(estimatedFrames, 1);
            }
        } catch (ArithmeticException | NumberFormatException e) {
            loggerPort.warn("[FramesExtractor][getTotalFramesFromVideo] Could not estimate from duration, error={}", e.getMessage());
        }

        loggerPort.warn("[FramesExtractor][getTotalFramesFromVideo] Using minimum frame count of 1");
        return 1;
    }


    private void writeFrameToZip(BufferedImage image, ZipOutputStream zos, int frameIndex)
            throws IOException {

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (IMAGE_FORMAT.equalsIgnoreCase(JPG_FORMAT)) {
                writeJpgWithQuality(image, baos, JPG_QUALITY);
            } else {
                ImageIO.write(image, IMAGE_FORMAT, baos);
            }

            final byte[] imageBytes = baos.toByteArray();
            final String entryName = String.format("%04d.%s", frameIndex, IMAGE_FORMAT);
            final ZipEntry entry = new ZipEntry(entryName);

            loggerPort.debug("[FramesExtractor][writeFrameToZip] Writing frame to ZIP, entryName={}, size={}bytes",
                    entryName, imageBytes.length);
            zos.putNextEntry(entry);
            zos.write(imageBytes);
            zos.closeEntry();
        }
    }

    private void writeJpgWithQuality(BufferedImage image, ByteArrayOutputStream baos, int quality) throws IOException {
        final ImageWriter writer = ImageIO.getImageWritersByFormatName(JPG_FORMAT).next();
        final ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality / (float) QUALITY_SCALE);

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private void handleExtractionError(Exception e, PipedOutputStream pos) {
        loggerPort.error("[FramesExtractor][handleExtractionError] Handling extraction error, error={}", e.getMessage());
        try {
            pos.close();
        } catch (IOException closingException) {
            loggerPort.warn("[FramesExtractor][handleExtractionError] Failed to close output stream during error handling");
        }
    }

    private void closeResourcesSafely(
            SeekableByteChannel ch,
            ZipOutputStream zos,
            PipedOutputStream pos,
            File tempFile
    ) {
        loggerPort.debug("[FramesExtractor][closeResourcesSafely] Closing all resources");
        closeZipOutputStream(zos);
        closeSeekableByteChannel(ch);
        closePipedOutputStream(pos);
        deleteTempFile(tempFile);
    }

    private void closeZipOutputStream(ZipOutputStream zos) {
        try {
            if (zos != null) {
                zos.close();
            }
        } catch (IOException e) {
            loggerPort.warn("[FramesExtractor][closeZipOutputStream] Failed to close ZipOutputStream");
        }
    }

    private void closeSeekableByteChannel(SeekableByteChannel ch) {
        try {
            if (ch != null) {
                ch.close();
            }
        } catch (IOException e) {
            loggerPort.warn("[FramesExtractor][closeSeekableByteChannel] Failed to close SeekableByteChannel");
        }
    }

    private void closePipedOutputStream(PipedOutputStream pos) {
        try {
            if (pos != null) {
                pos.close();
            }
        } catch (IOException e) {
            loggerPort.warn("[FramesExtractor][closePipedOutputStream] Failed to close PipedOutputStream");
        }
    }

    /**
     * Creates a temporary file with secure permissions (read/write for owner only).
     * Uses Java NIO Files API to ensure proper file permissions on Unix-like systems.
     *
     * @param prefix the prefix for the temp file name
     * @param suffix the suffix for the temp file name
     * @return a File object representing the securely created temporary file
     * @throws IOException if the temporary file cannot be created
     */
    private File createSecureTempFile(String prefix, String suffix) throws IOException {
        try {
            final Set<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );
            final FileAttribute<Set<PosixFilePermission>> attrs = PosixFilePermissions.asFileAttribute(permissions);

            final Path tempPath = Files.createTempFile(prefix, suffix, attrs);
            loggerPort.debug("[FramesExtractor][createSecureTempFile] Secure temp file created with restricted permissions, path={}", tempPath);
            return tempPath.toFile();
        } catch (UnsupportedOperationException e) {
            // POSIX permissions not supported (e.g., on Windows or certain filesystems)
            loggerPort.debug("[FramesExtractor][createSecureTempFile] POSIX permissions not supported, using default temp file creation");
            final Path tempPath = Files.createTempFile(prefix, suffix);
            final File tempFile = tempPath.toFile();

            // On Windows, set file to be readable/writable by owner only via file permissions
            try {
                if (!tempFile.setReadable(false, false)) { // Remove all other permissions
                    loggerPort.debug("[FramesExtractor][createSecureTempFile] Could not remove readable permission");
                }
                if (!tempFile.setWritable(false, false)) {
                    loggerPort.debug("[FramesExtractor][createSecureTempFile] Could not remove writable permission");
                }
                if (!tempFile.setExecutable(false, false)) {
                    loggerPort.debug("[FramesExtractor][createSecureTempFile] Could not remove executable permission");
                }
                if (!tempFile.setReadable(true, true)) {  // Add owner read
                    loggerPort.debug("[FramesExtractor][createSecureTempFile] Could not set owner readable permission");
                }
                if (!tempFile.setWritable(true, true)) {  // Add owner write
                    loggerPort.debug("[FramesExtractor][createSecureTempFile] Could not set owner writable permission");
                }
                loggerPort.debug("[FramesExtractor][createSecureTempFile] Applied file permissions for Windows, path={}", tempFile);
            } catch (SecurityException se) {
                loggerPort.warn("[FramesExtractor][createSecureTempFile] Could not set file permissions, error={}", se.getMessage());
            }

            return tempFile;
        }
    }

    private void deleteTempFile(File tempFile) {
        try {
            if (tempFile != null && tempFile.exists()) {
                if (!tempFile.delete()) {
                    tempFile.deleteOnExit();
                    loggerPort.debug("[FramesExtractor][deleteTempFile] Temp file scheduled for deletion, path={}", tempFile.getAbsolutePath());
                } else {
                    loggerPort.debug("[FramesExtractor][deleteTempFile] Temp file deleted, path={}", tempFile.getAbsolutePath());
                }
            }
        } catch (SecurityException e) {
            loggerPort.warn("[FramesExtractor][deleteTempFile] Failed to delete temp file");
        }
    }

    private void extractFramesWithMultiThread(
            File tempVideo,
            int totalFrames,
            ZipOutputStream zos
    ) {

        final BlockingQueue<Object> frameQueue = new LinkedBlockingQueue<>(queueCapacity);
        final ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize,
                r -> new Thread(r, THREAD_NAME_PREFIX + "-worker-" + System.currentTimeMillis()));

        final int totalFramesToExtract = (totalFrames + FPS - 1) / FPS;

        loggerPort.info(
                "[FramesExtractor][extractFramesWithMultiThread] Starting multi-thread extraction with {} threads, "
                        + "totalFramesToExtract={}, samplingRate=50%(1frame/2sec)",
                threadPoolSize, totalFramesToExtract);

        // Wrap ZipOutputStream with its own lock
        final ZipStreamWithLock zipStreamWithLock = new ZipStreamWithLock(zos);

        // Start ZIP writer thread
        final Thread zipWriterThread = new Thread(() -> {
            try {
                writeFramesToZip(frameQueue, zipStreamWithLock);
            } catch (IOException e) {
                loggerPort.error("[FramesExtractor][zipWriterThread] Error writing frames to ZIP, error={}", e.getMessage());
            }
        }, THREAD_NAME_PREFIX + "-zip-writer-" + System.currentTimeMillis());
        zipWriterThread.start();

        // Submit frame extraction tasks
        submitFrameExtractionTasks(tempVideo, totalFrames, frameQueue);

        // Wait for all tasks to complete
        shutdownExecutorService(executorService);

        // Signal end of frames
        signalFrameQueueEnd(frameQueue);

        // Wait for ZIP writer to finish
        waitForZipWriterThread(zipWriterThread);

        loggerPort.info("[FramesExtractor][extractFramesWithMultiThread] Multi-thread extraction completed successfully");
    }

    private void submitFrameExtractionTasks(File tempVideo, int totalFrames, BlockingQueue<Object> frameQueue) {
        int frameIndex = 0;
        for (int frameNumber = 0; frameNumber < totalFrames; frameNumber += FPS) {
            final int currentFrameIndex = frameIndex;

            try {
                final ExtractedFrame frame = extractFrameTask(tempVideo, frameNumber, currentFrameIndex);
                frameQueue.put(frame);
            } catch (InterruptedException e) {
                loggerPort.error("[FramesExtractor][submitFrameExtractionTasks] Interrupted while queuing frame {}, error={}",
                        frameNumber, e.getMessage());
                try {
                    frameQueue.put(new ExtractedFrame(currentFrameIndex));
                } catch (InterruptedException ie) {
                    loggerPort.error("[FramesExtractor][submitFrameExtractionTasks] Interrupted while queuing error, error={}",
                            ie.getMessage());
                }
                Thread.currentThread().interrupt();
            } catch (IOException | JCodecException e) {
                loggerPort.error("[FramesExtractor][submitFrameExtractionTasks] Error extracting frame {}, error={}",
                        frameNumber, e.getMessage());
                try {
                    frameQueue.put(new ExtractedFrame(currentFrameIndex));
                } catch (InterruptedException ie) {
                    loggerPort.error("[FramesExtractor][submitFrameExtractionTasks] Interrupted while queuing error, error={}",
                            ie.getMessage());
                    Thread.currentThread().interrupt();
                }
            }
            frameIndex++;
        }
    }

    private void shutdownExecutorService(ExecutorService executorService) {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(TERMINATION_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                loggerPort.warn("[FramesExtractor][shutdownExecutorService] Executor service did not terminate within timeout");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            loggerPort.error("[FramesExtractor][shutdownExecutorService] Interrupted waiting for executor service, error={}",
                    e.getMessage());
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void signalFrameQueueEnd(BlockingQueue<Object> frameQueue) {
        try {
            frameQueue.put(POISON_PILL);
        } catch (InterruptedException e) {
            loggerPort.error("[FramesExtractor][signalFrameQueueEnd] Interrupted while queuing poison pill, error={}",
                    e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    private void waitForZipWriterThread(Thread zipWriterThread) {
        try {
            zipWriterThread.join();
        } catch (InterruptedException e) {
            loggerPort.error("[FramesExtractor][waitForZipWriterThread] Interrupted waiting for ZIP writer thread, error={}",
                    e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    private ExtractedFrame extractFrameTask(File tempVideo, int frameNumber, int frameIndex) throws JCodecException, IOException {
        SeekableByteChannel ch = null;
        BufferedImage bufferedImage = null;

        try {
            ch = NIOUtils.readableChannel(tempVideo);
            final FrameGrab grab = FrameGrab.createFrameGrab(ch);

            loggerPort.debug("[FramesExtractor][extractFrameTask] {} Seeking to frame={}", getThreadInfo(), frameNumber);
            grab.seekToFramePrecise(frameNumber);

            final Picture picture = grab.getNativeFrame();

            if (picture == null) {
                loggerPort.warn("[FramesExtractor][extractFrameTask] {} Failed to grab frame at frameNumber={}", getThreadInfo(), frameNumber);
                return ExtractedFrame.empty(frameIndex);
            }

            bufferedImage = AWTUtil.toBufferedImage(picture);
            loggerPort.debug("[FramesExtractor][extractFrameTask] {} Successfully extracted frame frameIndex={}", getThreadInfo(), frameIndex);
            return new ExtractedFrame(frameIndex, bufferedImage);

        } catch (IOException | JCodecException e) {
            loggerPort.error("[FramesExtractor][extractFrameTask] {} Exception extracting frame {}, error={}",
                    getThreadInfo(), frameNumber, e.getMessage());
            return new ExtractedFrame(frameIndex);
        } finally {
            if (ch != null) {
                try {
                    ch.close();
                } catch (IOException e) {
                    loggerPort.warn("[FramesExtractor][extractFrameTask] {} Failed to close channel", getThreadInfo());
                }
            }
            if (bufferedImage != null) {
                bufferedImage.flush();
            }
        }
    }

    private void writeFramesToZip(BlockingQueue<Object> frameQueue, ZipStreamWithLock zipStreamWithLock) throws IOException {
        try {
            loggerPort.debug("[FramesExtractor][writeFramesToZip] {} Started ZIP writer thread", getThreadInfo());
            boolean running = true;
            while (running) {
                final Object frame = frameQueue.take();

                if (frame == POISON_PILL) {
                    loggerPort.debug("[FramesExtractor][writeFramesToZip] {} Received poison pill, terminating ZIP writer", getThreadInfo());
                    running = false;
                } else if (frame instanceof ExtractedFrame extractedFrame) {
                    if (!extractedFrame.isError()) {
                        final BufferedImage image = extractedFrame.getImage();
                        if (image != null) {
                            loggerPort.debug("[FramesExtractor][writeFramesToZip] {} Writing frame {} to ZIP",
                                    getThreadInfo(), extractedFrame.getFrameIndex());
                            zipStreamWithLock.writeFrameToZip(image, extractedFrame.getFrameIndex(), this::writeFrameToZip);
                        } else {
                            loggerPort.warn("[FramesExtractor][writeFramesToZip] {} Frame {} has null image",
                                    getThreadInfo(), extractedFrame.getFrameIndex());
                        }
                    } else {
                        loggerPort.warn("[FramesExtractor][writeFramesToZip] {} Skipping frame {} due to extraction error",
                                getThreadInfo(), extractedFrame.getFrameIndex());
                    }
                } else {
                    loggerPort.warn("[FramesExtractor][writeFramesToZip] {} Unexpected object type in queue", getThreadInfo());
                }
            }
        } catch (InterruptedException e) {
            loggerPort.error("[FramesExtractor][writeFramesToZip] {} Interrupted while consuming frames, error={}", getThreadInfo(), e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    private String getThreadInfo() {
        return "[Thread: " + Thread.currentThread().getName() + "]";
    }

    /**
     * Functional interface for writing a frame to ZIP.
     */
    @FunctionalInterface
    private interface ZipFrameWriter {
        void write(BufferedImage image, ZipOutputStream zos, int frameIndex) throws IOException;
    }

    /**
     * Inner class representing an extracted frame with error handling.
     */
    private static class ExtractedFrame {
        private final int frameIndex;
        private final BufferedImage image;
        private final boolean isError;

        ExtractedFrame(int frameIndex, BufferedImage image) {
            this.frameIndex = frameIndex;
            this.image = image;
            this.isError = false;
        }

        ExtractedFrame(int frameIndex) {
            this.frameIndex = frameIndex;
            this.image = null;
            this.isError = true;
        }

        static ExtractedFrame empty(int frameIndex) {
            return new ExtractedFrame(frameIndex, null);
        }

        int getFrameIndex() {
            return frameIndex;
        }

        BufferedImage getImage() {
            return image;
        }

        boolean isError() {
            return isError;
        }
    }

    /**
     * Wrapper class for ZipOutputStream that provides synchronized access via a dedicated lock object.
     * This avoids synchronizing directly on method parameters.
     */
    private static class ZipStreamWithLock {
        private final ZipOutputStream zos;
        private final Object lock = new Object();

        ZipStreamWithLock(ZipOutputStream zos) {
            this.zos = zos;
        }

        void writeFrameToZip(BufferedImage image, int frameIndex, ZipFrameWriter writer) throws IOException {
            synchronized (lock) {
                writer.write(image, zos, frameIndex);
            }
        }

        ZipOutputStream getZipOutputStream() {
            return zos;
        }
    }
}

