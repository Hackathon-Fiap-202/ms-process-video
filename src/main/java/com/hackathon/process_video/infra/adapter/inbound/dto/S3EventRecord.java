package com.hackathon.process_video.infra.adapter.inbound.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class S3EventRecord {
    @JsonProperty("eventVersion")
    private String eventVersion;

    @JsonProperty("eventSource")
    private String eventSource;

    @JsonProperty("awsRegion")
    private String awsRegion;

    @JsonProperty("eventTime")
    private String eventTime;

    @JsonProperty("eventName")
    private String eventName;

    @JsonProperty("userIdentity")
    private UserIdentity userIdentity;

    @JsonProperty("requestParameters")
    private RequestParameters requestParameters;

    @JsonProperty("responseElements")
    private ResponseElements responseElements;

    @JsonProperty("s3")
    private S3Entity s3;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserIdentity {
        @JsonProperty("principalId")
        private String principalId;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RequestParameters {
        @JsonProperty("sourceIPAddress")
        private String sourceIPAddress;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResponseElements {
        @JsonProperty("x-amz-request-id")
        private String xAmzRequestId;

        @JsonProperty("x-amz-id-2")
        private String xAmzId2;
    }
}
