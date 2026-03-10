package com.hackathon.processvideo.infra.adapter.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Generates manifest.json for processed video frames.
 * The manifest is included in the ZIP output and contains metadata about the extraction.
 */
@Component
public class ManifestGenerator {

    private static final String MANIFEST_FILENAME = "manifest.json";
    private final ObjectMapper objectMapper;

    public ManifestGenerator() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Generates a manifest JSON object containing metadata about the video processing.
     *
     * @param videoKey The original video file key in S3
     * @param frameCount The number of frames extracted
     * @return JSON string representation of the manifest
     */
    public String generateManifest(String videoKey, int frameCount) throws IOException {
        final Map<String, Object> manifest = new HashMap<>();

        // Execution metadata
        manifest.put("execution_id", UUID.randomUUID().toString());
        manifest.put("timestamp", Instant.now().toString());

        // Video metadata
        manifest.put("input_file", videoKey);
        manifest.put("frame_count", frameCount);
        manifest.put("image_format", "jpg");
        manifest.put("compression_quality", 85);
        manifest.put("sampling_rate", "50% (1 frame per 2 seconds)");

        // Processing details
        manifest.put("processor", "FramesExtractor");
        manifest.put("version", "1.0");
        manifest.put("status", "completed");

        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);
    }

    /**
     * Gets the filename for the manifest file.
     *
     * @return The filename "manifest.json"
     */
    public String getManifestFilename() {
        return MANIFEST_FILENAME;
    }
}
