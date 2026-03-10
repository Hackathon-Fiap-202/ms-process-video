package com.hackathon.processvideo.infra.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ManifestGenerator")
class ManifestGeneratorTest {

    private ManifestGenerator manifestGenerator;

    @BeforeEach
    void setUp() {
        manifestGenerator = new ManifestGenerator();
    }

    @Nested
    @DisplayName("getManifestFilename")
    class GetManifestFilenameTests {

        @Test
        @DisplayName("Should return manifest.json as the manifest filename")
        void shouldReturnManifestFilename() {
            // Act
            final String filename = manifestGenerator.getManifestFilename();

            // Assert
            assertThat(filename).isEqualTo("manifest.json");
        }
    }

    @Nested
    @DisplayName("generateManifest")
    class GenerateManifestTests {

        @Test
        @DisplayName("Should generate valid JSON manifest with correct fields")
        void shouldGenerateManifestWithCorrectFields() throws IOException {
            // Arrange
            final String videoKey = "videos/test-video.mp4";
            final int frameCount = 42;

            // Act
            final String manifest = manifestGenerator.generateManifest(videoKey, frameCount);

            // Assert
            assertThat(manifest)
                    .isNotNull()
                    .contains("\"input_file\"")
                    .contains("videos/test-video.mp4")
                    .contains("\"frame_count\"")
                    .contains("42")
                    .contains("\"image_format\"")
                    .contains("jpg")
                    .contains("\"status\"")
                    .contains("completed")
                    .contains("\"processor\"")
                    .contains("FramesExtractor")
                    .contains("\"version\"")
                    .contains("1.0")
                    .contains("\"execution_id\"")
                    .contains("\"timestamp\"");
        }

        @Test
        @DisplayName("Should generate different execution IDs on successive calls")
        void shouldGenerateDifferentExecutionIds() throws IOException {
            // Arrange
            final String videoKey = "videos/sample.mp4";

            // Act
            final String manifest1 = manifestGenerator.generateManifest(videoKey, 10);
            final String manifest2 = manifestGenerator.generateManifest(videoKey, 10);

            // Assert
            assertThat(manifest1).isNotEqualTo(manifest2);
        }

        @Test
        @DisplayName("Should include zero frame count when no frames extracted")
        void shouldHandleZeroFrameCount() throws IOException {
            // Arrange
            final String videoKey = "videos/empty.mp4";

            // Act
            final String manifest = manifestGenerator.generateManifest(videoKey, 0);

            // Assert
            assertThat(manifest)
                    .isNotNull()
                    .contains("0");
        }
    }
}
