package com.hackathon.process_video.infra.adapter.inbound.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class S3Object {
    @JsonProperty("key")
    private String key;

    @JsonProperty("sequencer")
    private String sequencer;

    @JsonProperty("eTag")
    private String eTag;

    @JsonProperty("size")
    private Long size;
}
