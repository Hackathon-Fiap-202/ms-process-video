package com.hackathon.processvideo.domain.service;

import com.hackathon.processvideo.domain.exception.VideoProcessingException;
import com.hackathon.processvideo.domain.port.in.ProcessVideoUseCase;
import com.hackathon.processvideo.domain.port.out.FileServicePort;
import com.hackathon.processvideo.domain.port.out.LoggerPort;
import com.hackathon.processvideo.domain.port.out.VideoFrameExtractorPort;
import com.hackathon.processvideo.domain.port.out.VideoStatusUpdatePort;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class VideoProcessorService implements ProcessVideoUseCase {

    private static final long AVG_FRAME_SIZE_COMPRESSED = 50L * 1024;
    private final FileServicePort fileServicePort;
    private final VideoFrameExtractorPort videoFrameExtractorPort;
    private final VideoStatusUpdatePort videoStatusUpdatePort;
    private final LoggerPort loggerPort;
    private final String outputBucketName;
    private final String outputPrefix;

    public VideoProcessorService(FileServicePort fileServicePort,
                                 VideoFrameExtractorPort videoFrameExtractorPort,
                                 VideoStatusUpdatePort videoStatusUpdatePort,
                                 LoggerPort loggerPort,
                                 @Value("${app.buckets.video-bucket-name}") String outputBucketName,
                                 @Value("${app.buckets.video-processed-prefix}") String outputPrefix) {
        this.fileServicePort = fileServicePort;
        this.videoFrameExtractorPort = videoFrameExtractorPort;
        this.videoStatusUpdatePort = videoStatusUpdatePort;
        this.loggerPort = loggerPort;
        this.outputBucketName = outputBucketName;
        this.outputPrefix = outputPrefix;
    }

    @Override
    @Async("videoProcessorExecutor")
    public void execute(String keyName, String bucketName) {
        boolean isSuccess = false;
        int frameCount = 0;
        long finalArchiveSize = 0;

        try {
            loggerPort.debug("[VideoProcessorService][execute] [Thread: {}] Starting execution, inputKey={}, inputBucket={}",
                    Thread.currentThread().getName(), keyName, bucketName);

            try (InputStream videoStream = fileServicePort.getFile(bucketName, keyName)) {
                loggerPort.debug("[VideoProcessorService][execute] [Thread: {}] Video file retrieved from S3, key={}",
                        Thread.currentThread().getName(), keyName);

                final String outputKey = formatOutputKey(keyName);
                final String entryPrefix = extractPrefixFromKey(keyName);
                loggerPort.debug("[VideoProcessorService][execute] [Thread: {}] Preparing frame extraction, outputKey={}, entryPrefix={}",
                        Thread.currentThread().getName(), outputKey, entryPrefix);

                try (InputStream zippedFrames = videoFrameExtractorPort.extractFramesAsZip(videoStream, entryPrefix)) {
                    loggerPort.info("[VideoProcessorService][execute] [Thread: {}] Frame extraction completed, uploading to output bucket={}",
                            Thread.currentThread().getName(), outputBucketName);
                    isSuccess = fileServicePort.uploadFile(outputBucketName, outputKey, zippedFrames);

                    if (isSuccess) {
                        finalArchiveSize = fileServicePort.getSize(outputBucketName, outputKey);
                        frameCount = estimateFrameCount(finalArchiveSize);
                        loggerPort.info("[VideoProcessorService][execute]"
                                        + " [Thread: {}] File uploaded successfully, outputKey={}, size={}bytes, estimatedFrames={}",
                                Thread.currentThread().getName(), outputKey, finalArchiveSize, frameCount);
                    } else {
                        loggerPort.error("[VideoProcessorService][execute] [Thread: {}] Upload failed for outputKey={}",
                                Thread.currentThread().getName(), outputKey);
                    }
                }

                if (isSuccess) {
                    loggerPort.debug("[VideoProcessorService][execute] [Thread: {}] Deleting source video, key={}, bucket={}",
                            Thread.currentThread().getName(), keyName, bucketName);
                    fileServicePort.deleteFile(bucketName, keyName);
                    loggerPort.info("[VideoProcessorService][execute] [Thread: {}] Source video deleted successfully",
                            Thread.currentThread().getName());
                }

            }
        } catch (IOException e) {
            loggerPort.error("[VideoProcessorService][execute] [Thread: {}] IO error during video processing, inputKey={}, error={}",
                    Thread.currentThread().getName(), keyName, e.getMessage());
        } catch (VideoProcessingException e) {
            loggerPort.error("[VideoProcessorService][execute] [Thread: {}] Video processing error, inputKey={}, error={}",
                    Thread.currentThread().getName(), keyName, e.getMessage());
        } finally {
            loggerPort.debug("[VideoProcessorService][execute] [Thread: {}] Publishing status update, success={}, frameCount={}",
                    Thread.currentThread().getName(), isSuccess, frameCount);
            videoStatusUpdatePort.notifyStatus(keyName, isSuccess, frameCount, finalArchiveSize);
        }
    }

    private String formatOutputKey(String originalKey) {
        final String filename = originalKey.substring(originalKey.lastIndexOf('/') + 1);
        final String nameWithoutExtension = filename.contains(".")
                ? filename.substring(0, filename.lastIndexOf('.'))
                : filename;
        return outputPrefix + "end-process/" + nameWithoutExtension + ".zip";
    }

    private String extractPrefixFromKey(String keyName) {
        final String filename = keyName.substring(keyName.lastIndexOf('/') + 1);
        return filename.contains(".")
                ? filename.substring(0, filename.lastIndexOf('.'))
                : filename;
    }

    private int estimateFrameCount(long archiveSize) {
        return (int) (archiveSize / AVG_FRAME_SIZE_COMPRESSED);
    }
}
