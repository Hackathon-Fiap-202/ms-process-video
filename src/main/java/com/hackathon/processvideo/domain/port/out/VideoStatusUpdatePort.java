package com.hackathon.processvideo.domain.port.out;

public interface VideoStatusUpdatePort {
    void notifyStatus(String videoKey, boolean success, int frameCount, long archiveSize);

    void notifyProcessing(String keyName);
}
