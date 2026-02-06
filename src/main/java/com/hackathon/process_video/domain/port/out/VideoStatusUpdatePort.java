package com.hackathon.process_video.domain.port.out;

public interface VideoStatusUpdatePort {
    void notifyStatus(String videoKey, boolean success, int frameCount, long archiveSize);
}
