package com.hackathon.processvideo.utils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.processvideo.infra.adapter.inbound.dto.S3EventNotification;
import com.hackathon.processvideo.utils.exception.ConversionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonConverterTest {

    private JsonConverter jsonConverter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        jsonConverter = new JsonConverter(objectMapper);
    }

    @Test
    void testToEventVideoWithValidJson() {
        // Arrange
        String validJson = "{\"Records\": []}";

        // Act
        S3EventNotification result = jsonConverter.toEventVideo(validJson);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getRecords());
    }

    @Test
    void testToEventVideoWithInvalidJson() {
        // Arrange
        String invalidJson = "{ invalid json }";

        // Act & Assert
        assertThrows(ConversionException.class, () -> jsonConverter.toEventVideo(invalidJson));
    }

    @Test
    void testToEventVideoWithEmptyString() {
        // Arrange
        String emptyJson = "";

        // Act & Assert
        assertThrows(ConversionException.class, () -> jsonConverter.toEventVideo(emptyJson));
    }

}