package com.hackathon.process_video.infra.adapter.inbound.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class S3Bucket {
    @JsonProperty("name")
    private String name;

    @JsonProperty("ownerIdentity")
    private OwnerIdentity ownerIdentity;

    @JsonProperty("arn")
    private String arn;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OwnerIdentity {
        @JsonProperty("principalId")
        private String principalId;
    }
}
