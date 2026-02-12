package com.hackathon.processvideo.utils;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.processvideo.infra.adapter.inbound.dto.S3EventNotification;
import com.hackathon.processvideo.utils.exception.ConversionException;
import org.springframework.stereotype.Component;

@Component
public class JsonConverter {

    private final ObjectMapper objectMapper;

    public JsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public S3EventNotification toEventVideo(String json) {
        try {
            return objectMapper.readValue(json, S3EventNotification.class);
        } catch (JacksonException e) {
            throw new ConversionException("Failed to deserialize EventVideo", e);
        }
    }
}
