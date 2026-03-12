package com.hackathon.processvideo.infra.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.processvideo.domain.model.enums.ProcessStatus;
import com.hackathon.processvideo.domain.model.enums.VideoStatusEventDTO;
import com.hackathon.processvideo.domain.port.out.LoggerPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@ExtendWith(MockitoExtension.class)
class SqsVideoStatusUpdateAdapterTest {

    private final String queueUrl = "http://test-queue-url";
    @Mock
    private SqsClient sqsClient;
    @Mock
    private LoggerPort loggerPort;
    @Mock
    private ObjectMapper objectMapper;
    private SqsVideoStatusUpdateAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SqsVideoStatusUpdateAdapter(sqsClient, loggerPort, objectMapper, queueUrl);
    }

    @Test
    @DisplayName("Should successfully send a PROCESSED status message to SQS")
    void notifyStatus_Success_Processed() throws JsonProcessingException {
        // Arrange
        String videoKey = "video123.mp4";
        String expectedJson = "{\"status\":\"PROCESSED\"}";

        when(objectMapper.writeValueAsString(any(VideoStatusEventDTO.class))).thenReturn(expectedJson);
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("msg-id-123").build());

        // Act
        adapter.notifyStatus(videoKey, true, 100, 5000L);

        // Assert
        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(requestCaptor.capture());

        SendMessageRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.queueUrl()).isEqualTo(queueUrl);
        assertThat(capturedRequest.messageBody()).isEqualTo(expectedJson);

        verify(loggerPort).info(contains("Status published to queue"), eq(videoKey), any(), eq(ProcessStatus.PROCESSED), anyInt(), anyLong());
    }

    @Test
    @DisplayName("Should send a FAILED status message when success parameter is false")
    void notifyStatus_Success_Failed() throws JsonProcessingException {
        // Arrange
        String videoKey = "video456.mp4";
        when(objectMapper.writeValueAsString(any(VideoStatusEventDTO.class))).thenReturn("{\"status\":\"FAILED\"}");
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("id").build());

        // Act
        adapter.notifyStatus(videoKey, false, 0, 0L);

        // Assert
        ArgumentCaptor<VideoStatusEventDTO> dtoCaptor = ArgumentCaptor.forClass(VideoStatusEventDTO.class);
        verify(objectMapper).writeValueAsString(dtoCaptor.capture());

        assertThat(dtoCaptor.getValue().status()).isEqualTo(ProcessStatus.FAILED);
    }

    @Test
    @DisplayName("Should handle JsonProcessingException and log error")
    void notifyStatus_HandlesSerializationError() throws JsonProcessingException {
        // Arrange
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Mocked error") {
        });

        // Act
        adapter.notifyStatus("key", true, 10, 100L);

        // Assert
        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
        verify(loggerPort).error(contains("Error serializing status to JSON"), anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle AWS SdkException and log error")
    void notifyStatus_HandlesSqsError() throws JsonProcessingException {
        // Arrange
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"status\":\"PROCESSED\"}");
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenThrow(SdkException.builder().message("AWS Error").build());

        // Act
        adapter.notifyStatus("key", true, 10, 100L);

        // Assert
        verify(loggerPort).error(contains("Error sending status to SQS"), anyString(), anyString());
    }
}