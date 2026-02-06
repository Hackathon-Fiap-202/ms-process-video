package com.hackathon.process_video.infra.adapter.outbound;

import com.hackathon.process_video.domain.port.out.VideoFrameExtractorPort;
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
    private static final int SECONDS_INTERVAL = 1;

    @Override
    public InputStream extractFramesAsZip(InputStream rawVideoData, String entryNamePrefix) {
        try {
            File tempVideo = File.createTempFile("streaming_extract_", ".mp4");
            try (FileOutputStream fos = new FileOutputStream(tempVideo)) {
                rawVideoData.transferTo(fos);
            }

            PipedOutputStream pos = new PipedOutputStream();
            PipedInputStream pis = new PipedInputStream(pos, 64 * 1024);

            Thread worker = new Thread(() -> {
                try (SeekableByteChannel ch = NIOUtils.readableChannel(tempVideo);
                     ZipOutputStream zos = new ZipOutputStream(pos)) {

                    FrameGrab grab = FrameGrab.createFrameGrab(ch);
                    double frameRate = grab.getVideoTrack().getMeta().getTotalFrames()
                            / grab.getVideoTrack().getMeta().getTotalDuration();
                    int totalFrames = grab.getVideoTrack().getMeta().getTotalFrames();
                    int frameInterval = SECONDS_INTERVAL <= 1 ? 1 : (int) (frameRate * SECONDS_INTERVAL);

                    int index = 0;
                    for (int frameNumber = 0; frameNumber < totalFrames; frameNumber += frameInterval) {
                        grab.seekToFramePrecise(frameNumber);
                        Picture pic = grab.getNativeFrame();
                        if (pic == null) continue;

                        BufferedImage img = AWTUtil.toBufferedImage(pic);

                        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                            ImageIO.write(img, IMAGE_FORMAT, baos);
                            byte[] imgBytes = baos.toByteArray();

                            String entryName = String.format("%s_frame_%04d.%s", entryNamePrefix, index++, IMAGE_FORMAT);
                            ZipEntry entry = new ZipEntry(entryName);
                            zos.putNextEntry(entry);
                            zos.write(imgBytes);
                            zos.closeEntry();
                        }
                    }
                } catch (IOException | JCodecException e) {
                    try {
                        pos.close();
                    } catch (IOException ex) {
                        // ignore
                    }
                } finally {
                    try {
                        if (tempVideo.exists()) tempVideo.delete();
                    } catch (Exception ignore) {}
                }
            }, "frame-zip-worker");

            worker.setDaemon(true);
            worker.start();

            return pis;

        } catch (IOException e) {
            throw new VideoProcessingException("Erro ao criar stream zipada de frames", e);
        }
    }
}