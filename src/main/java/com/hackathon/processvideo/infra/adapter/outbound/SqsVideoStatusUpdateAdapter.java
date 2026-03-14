package com.hackathon.processvideo.infra.adapter.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.processvideo.domain.model.enums.ProcessStatus;
import com.hackathon.processvideo.domain.model.enums.VideoStatusEventDTO;
import com.hackathon.processvideo.domain.port.out.LoggerPort;
import com.hackathon.processvideo.domain.port.out.VideoStatusUpdatePort;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;


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
            @Value("${app.sqs.video-updated-event-url:http://localhost:4566/000000000000/video-updated-event}") String queueUrl
    ) {
        this.sqsClient = sqsClient;
        this.loggerPort = loggerPort;
        this.objectMapper = objectMapper;
        this.queueUrl = queueUrl;
    }

    @Override
    public void notifyStatus(String videoKey, boolean success, int frameCount, long archiveSize) {
        try {
            loggerPort.debug("[SqsVideoStatusUpdateAdapter][notifyStatus] Building status event, videoKey={}, success={}", videoKey, success);
            final VideoStatusEventDTO eventDTO = buildEventDTO(videoKey, success, frameCount, archiveSize);

            if (eventDTO == null) {
                loggerPort.warn("[SqsVideoStatusUpdateAdapter][notifyStatus] Event DTO is null, skipping message, videoKey={}", videoKey);
                return;
            }

            final String messageBody = objectMapper.writeValueAsString(eventDTO);

            if (messageBody == null || messageBody.trim().isEmpty() || "{}".equals(messageBody.trim())) {
                loggerPort.warn("[SqsVideoStatusUpdateAdapter][notifyStatus] Serialized payload is empty, skipping message, videoKey={}", videoKey);
                return;
            }

            loggerPort.debug("[SqsVideoStatusUpdateAdapter][notifyStatus] Sending message to SQS queue, queueUrl={}", queueUrl);
            final SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .delaySeconds(0)
                    .build();

            final SendMessageResponse response = sqsClient.sendMessage(sendMessageRequest);

            loggerPort.info(
                    "[SqsVideoStatusUpdateAdapter][notifyStatus]"
                            + " Status published to queue, videoKey={}, messageId={}, status={}, frameCount={}, archiveSize={}bytes",
                    videoKey, response.messageId(), eventDTO.status(), frameCount, archiveSize);

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            loggerPort.error(
                    "[SqsVideoStatusUpdateAdapter][notifyStatus] Error serializing status to JSON, videoKey={}, error={}",
                    videoKey,
                    e.getMessage());
        } catch (software.amazon.awssdk.core.exception.SdkException e) {
            loggerPort.error(
                    "[SqsVideoStatusUpdateAdapter][notifyStatus] Error sending status to SQS, videoKey={}, error={}",
                    videoKey,
                    e.getMessage());
        }
    }

    @Override
    public void notifyProcessing(String videoKey) {
        try {
            loggerPort.debug("[SqsVideoStatusUpdateAdapter][notifyProcessing] Building processing event, videoKey={}", videoKey);

            final VideoStatusEventDTO eventDTO = new VideoStatusEventDTO(
                    videoKey,
                    false,
                    ProcessStatus.PROCESSING,
                    0,
                    0,
                    Instant.now().toString()
            );

            final String messageBody = objectMapper.writeValueAsString(eventDTO);

            final SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .delaySeconds(0)
                    .build();

            final SendMessageResponse response = sqsClient.sendMessage(sendMessageRequest);

            loggerPort.info("[SqsVideoStatusUpdateAdapter][notifyProcessing] Processing status published, videoKey={}, messageId={}",
                    videoKey, response.messageId());

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            loggerPort.error("[SqsVideoStatusUpdateAdapter][notifyProcessing] Error serializing event, videoKey={}, error={}",
                    videoKey, e.getMessage());
        } catch (software.amazon.awssdk.core.exception.SdkException e) {
            loggerPort.error("[SqsVideoStatusUpdateAdapter][notifyProcessing] Error sending to SQS, videoKey={}, error={}",
                    videoKey, e.getMessage());
        }
    }

    private VideoStatusEventDTO buildEventDTO(String videoKey, boolean success,
                                              int frameCount, long archiveSize) {
        final ProcessStatus status = success ? ProcessStatus.PROCESSED : ProcessStatus.FAILED;
        final String timestamp = Instant.now().toString();
        loggerPort.debug("[SqsVideoStatusUpdateAdapter][buildEventDTO] Created status event DTO, status={}, timestamp={}", status, timestamp);

        return new VideoStatusEventDTO(videoKey, success, status, frameCount, archiveSize, timestamp);
    }

}
