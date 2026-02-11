package com.hackathon.processvideo.infra.adapter.outbound;

import com.hackathon.processvideo.domain.port.out.VideoFrameExtractorPort;
import com.hackathon.processvideo.domain.port.out.LoggerPort;
import com.hackathon.processvideo.domain.exception.VideoProcessingException;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class FramesExtractor implements VideoFrameExtractorPort {

    private static final String IMAGE_FORMAT = "jpg";
    private static final int JPG_QUALITY = 85; // 0-100, 85-90 recommended for optimal quality/size tradeoff
    private static final int BUFFER_SIZE = 128 * 1024;
    private static final String THREAD_NAME_PREFIX = "video-frame-extractor";
    private static final int FPS = 60; // One frame every 2 seconds (30 FPS * 2) = 50% of seconds extracted
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
            loggerPort.debug("[FramesExtractor][extractFramesAsZip] Creating temporary video file");
            File tempVideo = File.createTempFile("streaming_extract_", ".mp4");
            try (FileOutputStream fos = new FileOutputStream(tempVideo)) {
                rawVideoData.transferTo(fos);
            }
            loggerPort.debug("[FramesExtractor][extractFramesAsZip] Temporary video created, path={}", tempVideo.getAbsolutePath());

            PipedOutputStream pos = new PipedOutputStream();
            PipedInputStream pis = new PipedInputStream(pos, BUFFER_SIZE);

            Thread worker = new Thread(() -> extractFramesInBackground(tempVideo, pos, entryNamePrefix),
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

    private void extractFramesInBackground(File tempVideo, PipedOutputStream pos, String entryNamePrefix) {
        SeekableByteChannel ch = null;
        ZipOutputStream zos = null;

        try {
            loggerPort.debug("[FramesExtractor][extractFramesInBackground] {} Opening video file channel", getThreadInfo());
            ch = NIOUtils.readableChannel(tempVideo);
            zos = new ZipOutputStream(pos);

            loggerPort.debug("[FramesExtractor][extractFramesInBackground] {} Creating frame grabber", getThreadInfo());
            FrameGrab grab = FrameGrab.createFrameGrab(ch);
            int totalFrames = getTotalFramesFromVideo(grab);

            if (totalFrames <= 0) {
                loggerPort.error("[FramesExtractor][extractFramesInBackground] {} No valid frames found in video", getThreadInfo());
                throw new VideoProcessingException("Video has no valid frames", null);
            }

            loggerPort.info("[FramesExtractor][extractFramesInBackground] {} Starting frame extraction, totalFrames={}, threadPoolSize={}, queueCapacity={}, imageFormat={}@{}%, samplingRate=50%(1frame/2sec)",
                    getThreadInfo(), totalFrames, threadPoolSize, queueCapacity, IMAGE_FORMAT, JPG_QUALITY);
            extractFramesWithMultiThread(tempVideo, totalFrames, zos, entryNamePrefix);

            loggerPort.debug("[FramesExtractor][extractFramesInBackground] {} Finishing ZIP output stream", getThreadInfo());
            zos.finish();
            loggerPort.info("[FramesExtractor][extractFramesInBackground] {} Frame extraction completed successfully", getThreadInfo());

        } catch (IOException | JCodecException e) {
            loggerPort.error("[FramesExtractor][extractFramesInBackground] {} Exception during frame extraction, error={}", getThreadInfo(), e.getMessage());
            handleExtractionError(e, pos);
        } finally {
            closeResourcesSafely(ch, zos, pos, tempVideo);
        }
    }

    private int getTotalFramesFromVideo(FrameGrab grab) throws JCodecException {
        try {
            loggerPort.debug("[FramesExtractor][getTotalFramesFromVideo] Retrieving total frame count from metadata");
            int totalFrames = grab.getVideoTrack().getMeta().getTotalFrames();

            if (totalFrames > 0) {
                loggerPort.debug("[FramesExtractor][getTotalFramesFromVideo] Frame count obtained from metadata, totalFrames={}", totalFrames);
                return totalFrames;
            }

            try {
                loggerPort.debug("[FramesExtractor][getTotalFramesFromVideo] Frame count invalid, estimating from duration");
                double totalDuration = grab.getVideoTrack().getMeta().getTotalDuration();
                if (totalDuration > 0) {
                    int estimatedFrames = (int) (totalDuration * 30);
                    loggerPort.debug("[FramesExtractor][getTotalFramesFromVideo] Estimated frame count, estimatedFrames={}", estimatedFrames);
                    return Math.max(estimatedFrames, 1);
                }
            } catch (Exception e) {
                loggerPort.warn("[FramesExtractor][getTotalFramesFromVideo] Could not estimate from duration, error={}", e.getMessage());
            }

            loggerPort.warn("[FramesExtractor][getTotalFramesFromVideo] Using minimum frame count of 1");
            return 1;

        } catch (Exception e) {
            loggerPort.error("[FramesExtractor][getTotalFramesFromVideo] Failed to retrieve frame metadata, error={}", e.getMessage());
            throw new JCodecException("Unable to retrieve frame metadata");
        }
    }

    private void extractFramesWithWindowSampling(FrameGrab grab, int totalFrames,
                                                 ZipOutputStream zos, String entryNamePrefix)
            throws JCodecException, IOException {
        // This method is deprecated - use extractFramesWithMultiThread instead
        throw new UnsupportedOperationException("Use extractFramesWithMultiThread instead");
    }

    private int extractFrameIfValid(FrameGrab grab, int frameNumber, ZipOutputStream zos,
                                    String entryNamePrefix, int frameIndex)
            throws JCodecException, IOException {
        // This method is deprecated - use extractFrameTask instead
        throw new UnsupportedOperationException("Use extractFrameTask instead");
    }

    private void writeFrameToZip(BufferedImage image, ZipOutputStream zos,
                                 String entryNamePrefix, int frameIndex)
            throws IOException {

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (IMAGE_FORMAT.equalsIgnoreCase("jpg")) {
                writeJpgWithQuality(image, baos, JPG_QUALITY);
            } else {
                ImageIO.write(image, IMAGE_FORMAT, baos);
            }

            byte[] imageBytes = baos.toByteArray();
            String entryName = String.format("%04d.%s", frameIndex, IMAGE_FORMAT);
            ZipEntry entry = new ZipEntry(entryName);

            loggerPort.debug("[FramesExtractor][writeFrameToZip] Writing frame to ZIP, entryName={}, size={}bytes",
                    entryName, imageBytes.length);
            zos.putNextEntry(entry);
            zos.write(imageBytes);
            zos.closeEntry();
        }
    }

    private void writeJpgWithQuality(BufferedImage image, ByteArrayOutputStream baos, int quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality / 100f);

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

    private void closeResourcesSafely(SeekableByteChannel ch, ZipOutputStream zos,
                                      PipedOutputStream pos, File tempFile) {
        loggerPort.debug("[FramesExtractor][closeResourcesSafely] Closing all resources");
        try {
            if (zos != null) {
                zos.close();
            }
        } catch (IOException e) {
            loggerPort.warn("[FramesExtractor][closeResourcesSafely] Failed to close ZipOutputStream");
        }

        try {
            if (ch != null) {
                ch.close();
            }
        } catch (IOException e) {
            loggerPort.warn("[FramesExtractor][closeResourcesSafely] Failed to close SeekableByteChannel");
        }

        try {
            if (pos != null) {
                pos.close();
            }
        } catch (IOException e) {
            loggerPort.warn("[FramesExtractor][closeResourcesSafely] Failed to close PipedOutputStream");
        }

        try {
            if (tempFile != null && tempFile.exists()) {
                if (!tempFile.delete()) {
                    tempFile.deleteOnExit();
                    loggerPort.debug("[FramesExtractor][closeResourcesSafely] Temp file scheduled for deletion, path={}", tempFile.getAbsolutePath());
                } else {
                    loggerPort.debug("[FramesExtractor][closeResourcesSafely] Temp file deleted, path={}", tempFile.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            loggerPort.warn("[FramesExtractor][closeResourcesSafely] Failed to delete temp file");
        }
    }

    private void extractFramesWithMultiThread(File tempVideo, int totalFrames,
                                              ZipOutputStream zos, String entryNamePrefix)
            throws JCodecException, IOException {

        BlockingQueue<Object> frameQueue = new LinkedBlockingQueue<>(queueCapacity);
        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize,
                r -> new Thread(r, THREAD_NAME_PREFIX + "-worker-" + System.currentTimeMillis()));

        int frameInterval = FPS; // One frame every 2 seconds (50% sampling)
        int totalFramesToExtract = (totalFrames + frameInterval - 1) / frameInterval;

        loggerPort.info("[FramesExtractor][extractFramesWithMultiThread] Starting multi-thread extraction with {} threads, totalFramesToExtract={}, samplingRate=50%(1frame/2sec)",
                threadPoolSize, totalFramesToExtract);

        // Start ZIP writer thread
        Thread zipWriterThread = new Thread(() -> {
            try {
                writeFramesToZip(frameQueue, zos, entryNamePrefix);
            } catch (IOException e) {
                loggerPort.error("[FramesExtractor][zipWriterThread] Error writing frames to ZIP, error={}", e.getMessage());
            }
        }, THREAD_NAME_PREFIX + "-zip-writer-" + System.currentTimeMillis());
        zipWriterThread.start();

        // Submit frame extraction tasks (50% sampling: 1 frame every 2 seconds)
        int frameIndex = 0;
        for (int frameNumber = 0; frameNumber < totalFrames; frameNumber += frameInterval) {
            final int currentFrameNumber = frameNumber;
            final int currentFrameIndex = frameIndex;

            executorService.submit(() -> {
                try {
                    ExtractedFrame frame = extractFrameTask(tempVideo, currentFrameNumber, currentFrameIndex);
                    frameQueue.put(frame);
                } catch (Exception e) {
                    loggerPort.error("[FramesExtractor][extractFrameTask] Error extracting frame {}, error={}", currentFrameNumber, e.getMessage());
                    try {
                        frameQueue.put(new ExtractedFrame(currentFrameIndex, e));
                    } catch (InterruptedException ie) {
                        loggerPort.error("[FramesExtractor][extractFrameTask] Interrupted while queuing error, error={}", ie.getMessage());
                    }
                }
            });
            frameIndex++;
        }

        // Wait for all tasks to complete
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.MINUTES)) {
                loggerPort.warn("[FramesExtractor][extractFramesWithMultiThread] Executor service did not terminate within timeout");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            loggerPort.error("[FramesExtractor][extractFramesWithMultiThread] Interrupted waiting for executor service, error={}", e.getMessage());
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Signal end of frames
        try {
            frameQueue.put(POISON_PILL);
        } catch (InterruptedException e) {
            loggerPort.error("[FramesExtractor][extractFramesWithMultiThread] Interrupted while queuing poison pill, error={}", e.getMessage());
            Thread.currentThread().interrupt();
        }

        // Wait for ZIP writer to finish
        try {
            zipWriterThread.join();
        } catch (InterruptedException e) {
            loggerPort.error("[FramesExtractor][extractFramesWithMultiThread] Interrupted waiting for ZIP writer thread, error={}", e.getMessage());
            Thread.currentThread().interrupt();
        }

        loggerPort.info("[FramesExtractor][extractFramesWithMultiThread] Multi-thread extraction completed successfully");
    }

    private ExtractedFrame extractFrameTask(File tempVideo, int frameNumber, int frameIndex) throws JCodecException, IOException {
        SeekableByteChannel ch = null;
        Picture picture = null;
        BufferedImage bufferedImage = null;

        try {
            ch = NIOUtils.readableChannel(tempVideo);
            FrameGrab grab = FrameGrab.createFrameGrab(ch);

            loggerPort.debug("[FramesExtractor][extractFrameTask] {} Seeking to frame={}", getThreadInfo(), frameNumber);
            grab.seekToFramePrecise(frameNumber);

            picture = grab.getNativeFrame();

            if (picture == null) {
                loggerPort.warn("[FramesExtractor][extractFrameTask] {} Failed to grab frame at frameNumber={}", getThreadInfo(), frameNumber);
                return ExtractedFrame.empty(frameIndex);
            }

            bufferedImage = AWTUtil.toBufferedImage(picture);
            loggerPort.debug("[FramesExtractor][extractFrameTask] {} Successfully extracted frame frameIndex={}", getThreadInfo(), frameIndex);
            return new ExtractedFrame(frameIndex, bufferedImage);

        } catch (Exception e) {
            loggerPort.error("[FramesExtractor][extractFrameTask] {} Exception extracting frame {}, error={}", getThreadInfo(), frameNumber, e.getMessage());
            return new ExtractedFrame(frameIndex, e);
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
            picture = null;
        }
    }

    private void writeFramesToZip(BlockingQueue<Object> frameQueue, ZipOutputStream zos, String entryNamePrefix) throws IOException {
        try {
            loggerPort.debug("[FramesExtractor][writeFramesToZip] {} Started ZIP writer thread", getThreadInfo());
            while (true) {
                Object frame = frameQueue.take();

                if (frame == POISON_PILL) {
                    loggerPort.debug("[FramesExtractor][writeFramesToZip] {} Received poison pill, terminating ZIP writer", getThreadInfo());
                    break;
                }

                if (!(frame instanceof ExtractedFrame)) {
                    loggerPort.warn("[FramesExtractor][writeFramesToZip] {} Unexpected object type in queue", getThreadInfo());
                    continue;
                }

                ExtractedFrame extractedFrame = (ExtractedFrame) frame;

                if (extractedFrame.isError()) {
                    loggerPort.warn("[FramesExtractor][writeFramesToZip] {} Skipping frame {} due to extraction error",
                            getThreadInfo(), extractedFrame.getFrameIndex());
                    continue;
                }

                BufferedImage image = extractedFrame.getImage();
                if (image == null) {
                    loggerPort.warn("[FramesExtractor][writeFramesToZip] {} Frame {} has null image", getThreadInfo(), extractedFrame.getFrameIndex());
                    continue;
                }

                synchronized (zos) {
                    loggerPort.debug("[FramesExtractor][writeFramesToZip] {} Writing frame {} to ZIP", getThreadInfo(), extractedFrame.getFrameIndex());
                    writeFrameToZip(image, zos, entryNamePrefix, extractedFrame.getFrameIndex());
                }
            }
        } catch (InterruptedException e) {
            loggerPort.error("[FramesExtractor][writeFramesToZip] {} Interrupted while consuming frames, error={}", getThreadInfo(), e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Inner class representing an extracted frame with error handling
     */
    private static class ExtractedFrame {
        private final int frameIndex;
        private final BufferedImage image;
        private final boolean isError;
        private final Exception exception;

        ExtractedFrame(int frameIndex, BufferedImage image) {
            this.frameIndex = frameIndex;
            this.image = image;
            this.isError = false;
            this.exception = null;
        }

        ExtractedFrame(int frameIndex, Exception exception) {
            this.frameIndex = frameIndex;
            this.image = null;
            this.isError = true;
            this.exception = exception;
        }

        static ExtractedFrame empty(int frameIndex) {
            return new ExtractedFrame(frameIndex, (BufferedImage) null);
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

        Exception getException() {
            return exception;
        }
    }

    private String getThreadInfo() {
        return "[Thread: " + Thread.currentThread().getName() + "]";
    }
}

