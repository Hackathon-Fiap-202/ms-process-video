package com.hackathon.process_video.domain.service;

import com.hackathon.process_video.domain.port.in.ProcessVideoUseCase;
import com.hackathon.process_video.domain.port.out.FileServicePort;
import com.hackathon.process_video.domain.port.out.LoggerPort;
import com.hackathon.process_video.domain.port.out.VideoStatusUpdatePort;
import com.hackathon.process_video.domain.port.out.VideoFrameExtractorPort;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class VideoProcessorService implements ProcessVideoUseCase {

    private final FileServicePort fileServicePort;
    private final VideoFrameExtractorPort videoFrameExtractorPort;
    private final VideoStatusUpdatePort videoStatusUpdatePort;
    private final LoggerPort loggerPort;

    public VideoProcessorService(FileServicePort fileServicePort,
                                 VideoFrameExtractorPort videoFrameExtractorPort,
                                 VideoStatusUpdatePort videoStatusUpdatePort,
                                 LoggerPort loggerPort) {
        this.fileServicePort = fileServicePort;
        this.videoFrameExtractorPort = videoFrameExtractorPort;
        this.videoStatusUpdatePort = videoStatusUpdatePort;
        this.loggerPort = loggerPort;
    }

    @Override
    public void execute(String keyName, String bucketName) {
        boolean isSuccess = false;
        int frameCount = 0;
        long finalArchiveSize = 0;

        try (InputStream videoStream = fileServicePort.getFile(bucketName, keyName)) {

            String outputKey = formatOutputKey(keyName);
            String entryPrefix = extractPrefixFromKey(keyName);

            try (InputStream zippedFrames = videoFrameExtractorPort.extractFramesAsZip(videoStream, entryPrefix)) {
                isSuccess = fileServicePort.uploadFile(bucketName, outputKey, zippedFrames);
                if (isSuccess) {
                    finalArchiveSize = fileServicePort.getSize(bucketName, outputKey);
                    frameCount = estimateFrameCount(finalArchiveSize);
                }
            }

            // Deleta o vídeo original após sucesso
            fileServicePort.deleteFile(bucketName, keyName);

        } catch (Exception e) {
            loggerPort.error("Falha crítica ao processar vídeo {}: {}", keyName, e.getMessage());
            e.printStackTrace();
        } finally {
            videoStatusUpdatePort.notifyStatus(keyName, isSuccess, frameCount, finalArchiveSize);
        }
    }

    private String formatOutputKey(String originalKey) {
        return originalKey.replace(".mp4", "")
                .replace("videos/", "frames/") + ".zip";
    }

    private String extractPrefixFromKey(String keyName) {
        // Extrai o nome do arquivo sem extensão para usar como prefixo no ZIP
        return keyName.substring(keyName.lastIndexOf('/') + 1)
                .replace(".mp4", "");
    }

    private int estimateFrameCount(long archiveSize) {
        // Estimativa: média de 50KB por frame PNG comprimido
        // Ajuste este valor baseado em seu caso de uso
        long avgFrameSizeCompressed = 50 * 1024;
        return (int) (archiveSize / avgFrameSizeCompressed);
    }
}