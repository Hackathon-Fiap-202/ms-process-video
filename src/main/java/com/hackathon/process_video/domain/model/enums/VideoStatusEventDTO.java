package com.hackathon.process_video.domain.model.enums;

public record VideoStatusEventDTO(
        String videoKey,
        boolean success,
        ProcessStatus status,
        int frameCount,
        long archiveSize,
        String timestamp
) {
}
