package com.hackathon.process_video.infra.adapter.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.process_video.domain.model.enums.ProcessStatus;
import com.hackathon.process_video.domain.model.enums.VideoStatusEventDTO;
import com.hackathon.process_video.domain.port.out.LoggerPort;
import com.hackathon.process_video.domain.port.out.VideoStatusUpdatePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import java.time.Instant;


@Component
public class SqsVideoStatusUpdateAdapter implements VideoStatusUpdatePort {

    private final SqsClient sqsClient;
    private final LoggerPort loggerPort;
    private final ObjectMapper objectMapper;
    private final String queueUrl;

    public SqsVideoStatusUpdateAdapter(
            SqsClient sqsClient,
            LoggerPort loggerPort,
            ObjectMapper objectMapper,
            @Value("${spring.cloud.aws.sqs.video-updated-event-url}") String queueUrl
    ) {
        this.sqsClient = sqsClient;
        this.loggerPort = loggerPort;
        this.objectMapper = objectMapper;
        this.queueUrl = queueUrl;
    }

    @Override
    public void notifyStatus(String videoKey, boolean success, int frameCount, long archiveSize) {
        try {
            VideoStatusEventDTO eventDTO = buildEventDTO(videoKey, success, frameCount, archiveSize);
            String messageBody = objectMapper.writeValueAsString(eventDTO);

            SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .delaySeconds(0)
                    .build();

            SendMessageResponse response = sqsClient.sendMessage(sendMessageRequest);

            loggerPort.info("Status do vídeo {} publicado na fila. MessageId: {}, Status: {}, Frames: {}, Tamanho: {} bytes",
                    videoKey, response.messageId(), eventDTO.status(), frameCount, archiveSize);

        } catch (Exception e) {
            loggerPort.error("Erro ao enviar status do vídeo {} para SQS: {}",
                    videoKey, e.getMessage());
        }
    }

    private VideoStatusEventDTO buildEventDTO(String videoKey, boolean success,
                                              int frameCount, long archiveSize) {
        ProcessStatus status = success ? ProcessStatus.PROCESSED : ProcessStatus.FAILED;
        String timestamp = Instant.now().toString();

        return new VideoStatusEventDTO(videoKey, success, status, frameCount, archiveSize, timestamp);
    }


}