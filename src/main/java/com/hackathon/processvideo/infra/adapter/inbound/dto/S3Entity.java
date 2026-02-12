package com.hackathon.processvideo.infra.adapter.inbound.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class S3Entity {
    @JsonProperty("s3SchemaVersion")
    private String s3SchemaVersion;

    @JsonProperty("configurationId")
    private String configurationId;

    @JsonProperty("bucket")
    private S3Bucket bucket;

    @JsonProperty("object")
    private S3Object object;

}
