package com.hackathon.processvideo.infra.adapter.inbound.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class S3EventNotification {
    @JsonProperty("Records")
    private List<S3EventRecord> records;


}
