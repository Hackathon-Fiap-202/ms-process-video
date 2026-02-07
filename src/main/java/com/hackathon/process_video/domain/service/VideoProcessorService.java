package com.hackathon.process_video.domain.service;

import com.hackathon.process_video.domain.port.in.ProcessVideoUseCase;
import com.hackathon.process_video.domain.port.out.FileServicePort;
import com.hackathon.process_video.domain.port.out.LoggerPort;
import com.hackathon.process_video.domain.port.out.VideoStatusUpdatePort;
import com.hackathon.process_video.domain.port.out.VideoFrameExtractorPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class VideoProcessorService implements ProcessVideoUseCase {

    private final FileServicePort fileServicePort;
    private final VideoFrameExtractorPort videoFrameExtractorPort;
    private final VideoStatusUpdatePort videoStatusUpdatePort;
    private final LoggerPort loggerPort;
    private final String outputBucketName;

    public VideoProcessorService(FileServicePort fileServicePort,
                                 VideoFrameExtractorPort videoFrameExtractorPort,
                                 VideoStatusUpdatePort videoStatusUpdatePort,
                                 LoggerPort loggerPort,
                                 @Value("${app.buckets.video-processed-storage}") String outputBucketName) {
        this.fileServicePort = fileServicePort;
        this.videoFrameExtractorPort = videoFrameExtractorPort;
        this.videoStatusUpdatePort = videoStatusUpdatePort;
        this.loggerPort = loggerPort;
        this.outputBucketName = outputBucketName;
    }

    @Override
    public void execute(String keyName, String bucketName) {
        boolean isSuccess = false;
        int frameCount = 0;
        long finalArchiveSize = 0;

        try {
            loggerPort.debug("[VideoProcessorService][execute] Starting execution, inputKey={}, inputBucket={}", keyName, bucketName);

            try (InputStream videoStream = fileServicePort.getFile(bucketName, keyName)) {
                loggerPort.debug("[VideoProcessorService][execute] Video file retrieved from S3, key={}", keyName);

                String outputKey = formatOutputKey(keyName);
                String entryPrefix = extractPrefixFromKey(keyName);
                loggerPort.debug("[VideoProcessorService][execute] Preparing frame extraction, outputKey={}, entryPrefix={}", outputKey, entryPrefix);

                try (InputStream zippedFrames = videoFrameExtractorPort.extractFramesAsZip(videoStream, entryPrefix)) {
                    loggerPort.info("[VideoProcessorService][execute] Frame extraction completed, uploading to output bucket={}", outputBucketName);
                    isSuccess = fileServicePort.uploadFile(outputBucketName, outputKey, zippedFrames);

                    if (isSuccess) {
                        finalArchiveSize = fileServicePort.getSize(outputBucketName, outputKey);
                        frameCount = estimateFrameCount(finalArchiveSize);
                        loggerPort.info("[VideoProcessorService][execute] File uploaded successfully, outputKey={}, size={}bytes, estimatedFrames={}",
                                outputKey, finalArchiveSize, frameCount);
                    } else {
                        loggerPort.error("[VideoProcessorService][execute] Upload failed for outputKey={}", outputKey);
                    }
                }

                if (isSuccess) {
                    loggerPort.debug("[VideoProcessorService][execute] Deleting source video, key={}, bucket={}", keyName, bucketName);
                    fileServicePort.deleteFile(bucketName, keyName);
                    loggerPort.info("[VideoProcessorService][execute] Source video deleted successfully");
                }

            }
        } catch (Exception e) {
            loggerPort.error("[VideoProcessorService][execute] Critical failure during video processing, inputKey={}, error={}",
                    keyName, e.getMessage());
            e.printStackTrace();
        } finally {
            loggerPort.debug("[VideoProcessorService][execute] Publishing status update, success={}, frameCount={}", isSuccess, frameCount);
            videoStatusUpdatePort.notifyStatus(keyName, isSuccess, frameCount, finalArchiveSize);
        }
    }

    private String formatOutputKey(String originalKey) {
        String filename = originalKey.substring(originalKey.lastIndexOf('/') + 1);
        String nameWithoutExtension = filename.contains(".")
                ? filename.substring(0, filename.lastIndexOf('.'))
                : filename;
        return "frames/" + nameWithoutExtension + ".zip";
    }

    private String extractPrefixFromKey(String keyName) {
        String filename = keyName.substring(keyName.lastIndexOf('/') + 1);
        return filename.contains(".")
                ? filename.substring(0, filename.lastIndexOf('.'))
                : filename;
    }

    private int estimateFrameCount(long archiveSize) {
        long avgFrameSizeCompressed = 50 * 1024;
        return (int) (archiveSize / avgFrameSizeCompressed);
    }
}