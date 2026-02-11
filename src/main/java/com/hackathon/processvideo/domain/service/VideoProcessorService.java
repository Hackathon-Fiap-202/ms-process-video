package com.hackathon.processvideo.domain.service;

import com.hackathon.processvideo.domain.port.in.ProcessVideoUseCase;
import com.hackathon.processvideo.domain.port.out.FileServicePort;
import com.hackathon.processvideo.domain.port.out.LoggerPort;
import com.hackathon.processvideo.domain.port.out.VideoStatusUpdatePort;
import com.hackathon.processvideo.domain.port.out.VideoFrameExtractorPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
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

                String outputKey = formatOutputKey(keyName);
                String entryPrefix = extractPrefixFromKey(keyName);
                loggerPort.debug("[VideoProcessorService][execute] [Thread: {}] Preparing frame extraction, outputKey={}, entryPrefix={}",
                    Thread.currentThread().getName(), outputKey, entryPrefix);

                try (InputStream zippedFrames = videoFrameExtractorPort.extractFramesAsZip(videoStream, entryPrefix)) {
                    loggerPort.info("[VideoProcessorService][execute] [Thread: {}] Frame extraction completed, uploading to output bucket={}",
                        Thread.currentThread().getName(), outputBucketName);
                    isSuccess = fileServicePort.uploadFile(outputBucketName, outputKey, zippedFrames);

                    if (isSuccess) {
                        finalArchiveSize = fileServicePort.getSize(outputBucketName, outputKey);
                        frameCount = estimateFrameCount(finalArchiveSize);
                        loggerPort.info("[VideoProcessorService][execute] [Thread: {}] File uploaded successfully, outputKey={}, size={}bytes, estimatedFrames={}",
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
        } catch (Exception e) {
            loggerPort.error("[VideoProcessorService][execute] [Thread: {}] Critical failure during video processing, inputKey={}, error={}",
                    Thread.currentThread().getName(), keyName, e.getMessage());
            e.printStackTrace();
        } finally {
            loggerPort.debug("[VideoProcessorService][execute] [Thread: {}] Publishing status update, success={}, frameCount={}",
                Thread.currentThread().getName(), isSuccess, frameCount);
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