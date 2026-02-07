package com.hackathon.process_video.infra.adapter.outbound;

import com.hackathon.process_video.domain.port.out.VideoFrameExtractorPort;
import com.hackathon.process_video.domain.port.out.LoggerPort;
import com.hackathon.process_video.domain.exception.VideoProcessingException;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class FramesExtractor implements VideoFrameExtractorPort {

    private static final String IMAGE_FORMAT = "png";
    private static final int WINDOW_SIZE = 60;
    private static final int BUFFER_SIZE = 128 * 1024;
    private static final String THREAD_NAME_PREFIX = "video-frame-extractor";

    private final LoggerPort loggerPort;

    public FramesExtractor(LoggerPort loggerPort) {
        this.loggerPort = loggerPort;
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
            loggerPort.debug("[FramesExtractor][extractFramesInBackground] Opening video file channel");
            ch = NIOUtils.readableChannel(tempVideo);
            zos = new ZipOutputStream(pos);

            loggerPort.debug("[FramesExtractor][extractFramesInBackground] Creating frame grabber");
            FrameGrab grab = FrameGrab.createFrameGrab(ch);
            int totalFrames = getTotalFramesFromVideo(grab);

            if (totalFrames <= 0) {
                loggerPort.error("[FramesExtractor][extractFramesInBackground] No valid frames found in video");
                throw new VideoProcessingException("Video has no valid frames", null);
            }

            loggerPort.info("[FramesExtractor][extractFramesInBackground] Starting frame extraction, totalFrames={}", totalFrames);
            extractFramesWithWindowSampling(grab, totalFrames, zos, entryNamePrefix);

            loggerPort.debug("[FramesExtractor][extractFramesInBackground] Finishing ZIP output stream");
            zos.finish();
            loggerPort.info("[FramesExtractor][extractFramesInBackground] Frame extraction completed successfully");

        } catch (IOException | JCodecException e) {
            loggerPort.error("[FramesExtractor][extractFramesInBackground] Exception during frame extraction, error={}", e.getMessage());
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

        int extractedFrameIndex = 0;
        int windowCount = 0;

        for (int windowStart = 0; windowStart < totalFrames; windowStart += WINDOW_SIZE) {
            windowCount++;
            int windowEnd = Math.min(windowStart + WINDOW_SIZE - 1, totalFrames - 1);
            int windowMid = windowStart + (WINDOW_SIZE / 2);

            loggerPort.debug("[FramesExtractor][extractFramesWithWindowSampling] Processing window={}, start={}, end={}, mid={}",
                windowCount, windowStart, windowEnd, windowMid);

            extractedFrameIndex += extractFrameIfValid(grab, windowStart, zos, entryNamePrefix, extractedFrameIndex);

            if (windowMid <= windowEnd) {
                extractedFrameIndex += extractFrameIfValid(grab, windowMid, zos, entryNamePrefix, extractedFrameIndex);
            }

            if (windowEnd > windowStart) {
                extractedFrameIndex += extractFrameIfValid(grab, windowEnd, zos, entryNamePrefix, extractedFrameIndex);
            }
        }

        loggerPort.info("[FramesExtractor][extractFramesWithWindowSampling] Window sampling completed, totalWindows={}, totalExtractedFrames={}",
            windowCount, extractedFrameIndex);
    }

    private int extractFrameIfValid(FrameGrab grab, int frameNumber, ZipOutputStream zos,
                                     String entryNamePrefix, int frameIndex)
            throws JCodecException, IOException {

        Picture picture = null;
        BufferedImage bufferedImage = null;

        try {
            loggerPort.debug("[FramesExtractor][extractFrameIfValid] Seeking to frame={}", frameNumber);
            grab.seekToFramePrecise(frameNumber);
            picture = grab.getNativeFrame();

            if (picture == null) {
                loggerPort.warn("[FramesExtractor][extractFrameIfValid] Failed to grab frame at frameNumber={}", frameNumber);
                return 0;
            }

            bufferedImage = AWTUtil.toBufferedImage(picture);
            writeFrameToZip(bufferedImage, zos, entryNamePrefix, frameIndex);
            return 1;

        } finally {
            if (bufferedImage != null) {
                bufferedImage.flush();
                bufferedImage = null;
            }
            picture = null;
        }
    }

    private void writeFrameToZip(BufferedImage image, ZipOutputStream zos,
                                  String entryNamePrefix, int frameIndex)
            throws IOException {

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, IMAGE_FORMAT, baos);
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
}