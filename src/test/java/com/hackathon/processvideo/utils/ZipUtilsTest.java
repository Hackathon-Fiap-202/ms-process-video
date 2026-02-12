package com.hackathon.processvideo.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hackathon.processvideo.domain.exception.VideoProcessingException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCompressFilesSuccessfully() throws IOException {
        // GIVEN: Two dummy files
        Path file1 = tempDir.resolve("frame1.png");
        Files.writeString(file1, "content-frame-1");

        Path file2 = tempDir.resolve("frame2.png");
        Files.writeString(file2, "content-frame-2");

        List<File> filesToZip = List.of(file1.toFile(), file2.toFile());

        // WHEN: Compressing the files
        InputStream zipResult = ZipUtils.compressFiles(filesToZip);

        // THEN: The result is not null
        assertThat(zipResult).isNotNull();

        // AND: The ZIP contains the expected entries
        try (ZipInputStream zis = new ZipInputStream(zipResult)) {
            ZipEntry entry1 = zis.getNextEntry();
            assertThat(entry1.getName()).isEqualTo("frame1.png");

            ZipEntry entry2 = zis.getNextEntry();
            assertThat(entry2.getName()).isEqualTo("frame2.png");

            assertThat(zis.getNextEntry()).isNull(); // No more entries
        }

        // AND: The original files should have been deleted by ZipUtils
        assertThat(file1.toFile()).doesNotExist();
        assertThat(file2.toFile()).doesNotExist();
    }

    @Test
    void shouldThrowExceptionWhenFileDoesNotExist() {
        // GIVEN: A file that doesn't actually exist on disk
        File nonExistentFile = new File(tempDir.toFile(), "ghost.png");
        List<File> filesToZip = List.of(nonExistentFile);

        // WHEN / THEN: It should wrap the IOException into a VideoProcessingException
        assertThatThrownBy(() -> ZipUtils.compressFiles(filesToZip))
                .isInstanceOf(VideoProcessingException.class)
                .hasMessageContaining("Erro ao zipar");
    }

    @Test
    void shouldHandleEmptyList() throws IOException {
        // GIVEN: An empty list
        List<File> emptyList = List.of();

        // WHEN: Compressing
        InputStream zipResult = ZipUtils.compressFiles(emptyList);

        // THEN: It produces a valid (but empty) ZIP stream
        try (ZipInputStream zis = new ZipInputStream(zipResult)) {
            assertThat(zis.getNextEntry()).isNull();
        }
    }
}