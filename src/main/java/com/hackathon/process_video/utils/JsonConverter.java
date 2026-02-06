package com.hackathon.process_video.utils;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.process_video.infra.adapter.inbound.dto.EventVideo;
import com.hackathon.process_video.utils.exception.ConversionException;
import org.springframework.stereotype.Component;

@Component
public class JsonConverter {

    private static final String ERROR_CONVERT_JSON = "[JsonConverter][ERROR] Failed to convert JSON to Event: {}";
    private static final String ERROR_CONVERT_OBJ = "[JsonConverter][ERROR] Failed to convert object to JSON: {}";

    private final ObjectMapper objectMapper;

    public JsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EventVideo toEventVideo(String json) {
        try {
            return objectMapper.readValue(json, EventVideo.class);
        } catch (JacksonException e) {
            throw new ConversionException("Failed to deserialize EventVideo", e);
        }
    }
}
