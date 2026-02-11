package com.hackathon.processvideo.infra.adapter.inbound.dto;

import com.hackathon.processvideo.domain.model.enums.ProcessStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;


@NoArgsConstructor
@Getter
public class EventVideoUpdate {

    private ProcessStatus status;
    private String keyName;
    private String bucketName;

    @Builder
    public EventVideoUpdate(ProcessStatus status, String keyName, String bucketName) {
        if (status == null) {
            throw new IllegalArgumentException("Status is mandatory");
        }
        if (!StringUtils.hasText(keyName)) {
            throw new IllegalArgumentException("Key name is mandatory");
        }
        if (!StringUtils.hasText(bucketName)) {
            throw new IllegalArgumentException("Bucket name is mandatory");
        }
        this.keyName = keyName;
        this.bucketName = bucketName;
        this.status = status;
    }

}
