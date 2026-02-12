package com.hackathon.processvideo.domain.port.out;

import java.io.InputStream;

public interface VideoFrameExtractorPort {

    InputStream extractFramesAsZip(InputStream videoStream, String entryNamePrefix);
}
