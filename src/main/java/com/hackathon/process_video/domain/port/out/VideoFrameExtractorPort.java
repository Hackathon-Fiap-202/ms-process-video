package com.hackathon.process_video.domain.port.out;

import java.io.File;
import java.io.InputStream;
import java.util.List;

public interface VideoFrameExtractorPort {

    InputStream extractFramesAsZip(InputStream videoStream, String entryNamePrefix);
}
