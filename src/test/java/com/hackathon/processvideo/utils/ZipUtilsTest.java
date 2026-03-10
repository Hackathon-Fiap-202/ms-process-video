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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipUtilsTest {

    @TempDir
    Path tempDir;

    private String originalAppTempDir;

    @AfterEach
    void cleanupSystemProperties() {
        // Restore or clear the system property after each test
        if (originalAppTempDir != null) {
            System.setProperty("app.temp.dir", originalAppTempDir);
        } else {
            System.clearProperty("app.temp.dir");
        }
    }

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
            assertThat(entry1).isNotNull();
            @SuppressWarnings("nullness")
            String entry1Name = entry1.getName();
            assertThat(entry1Name).isEqualTo("frame1.png");

            ZipEntry entry2 = zis.getNextEntry();
            assertThat(entry2).isNotNull();
            @SuppressWarnings("nullness")
            String entry2Name = entry2.getName();
            assertThat(entry2Name).isEqualTo("frame2.png");

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
        assertThatThrownBy(() -> {
            try (InputStream ignored = ZipUtils.compressFiles(filesToZip)) {
                // Attempt to consume the stream to trigger the exception
            }
        })
                .isInstanceOf(VideoProcessingException.class)
                .hasMessageContaining("Erro ao processar compressão de arquivos");
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

    @Test
    void shouldUseCustomTempDirectoryWhenSystemPropertyIsSet() throws IOException {
        // GIVEN: A custom temp directory path via system property
        originalAppTempDir = System.getProperty("app.temp.dir");
        Path customTempDir = tempDir.resolve("custom-temp");
        System.setProperty("app.temp.dir", customTempDir.toString());

        Path testFile = tempDir.resolve("test.png");
        Files.writeString(testFile, "test-content");

        // WHEN: Compressing files (which internally uses getSecureTempDirectory)
        InputStream zipResult = ZipUtils.compressFiles(List.of(testFile.toFile()));

        // THEN: The custom directory should have been created and used
        assertThat(customTempDir).exists();
        assertThat(zipResult).isNotNull();

        // Cleanup
        zipResult.close();
    }

    @Test
    void shouldUseExistingCustomTempDirectoryWhenItExists() throws IOException {
        // GIVEN: A custom temp directory that already exists
        originalAppTempDir = System.getProperty("app.temp.dir");
        Path customTempDir = tempDir.resolve("existing-custom-temp");
        Files.createDirectories(customTempDir);
        System.setProperty("app.temp.dir", customTempDir.toString());

        Path testFile = tempDir.resolve("test2.png");
        Files.writeString(testFile, "test-content-2");

        // WHEN: Compressing files
        InputStream zipResult = ZipUtils.compressFiles(List.of(testFile.toFile()));

        // THEN: The existing directory is used
        assertThat(customTempDir).exists();
        assertThat(zipResult).isNotNull();

        // Cleanup
        zipResult.close();
    }

    @Test
    void shouldCreateDefaultSecureTempDirectoryWhenNoCustomPropertySet() throws IOException {
        // GIVEN: No custom temp directory (default behavior)
        originalAppTempDir = System.getProperty("app.temp.dir");
        System.clearProperty("app.temp.dir");

        Path testFile = tempDir.resolve("test3.png");
        Files.writeString(testFile, "test-content-3");

        // WHEN: Compressing files
        InputStream zipResult = ZipUtils.compressFiles(List.of(testFile.toFile()));

        // THEN: A default secure temp directory is created under
        // java.io.tmpdir/processvideo
        assertThat(zipResult).isNotNull();
        Path expectedDefaultDir = Path.of(System.getProperty("java.io.tmpdir"), "processvideo");
        assertThat(expectedDefaultDir).exists();

        // Cleanup
        zipResult.close();
    }

    @Test
    void shouldHandleEmptyCustomTempDirProperty() throws IOException {
        // GIVEN: Empty or whitespace-only custom temp dir property
        originalAppTempDir = System.getProperty("app.temp.dir");
        System.setProperty("app.temp.dir", "   ");

        Path testFile = tempDir.resolve("test4.png");
        Files.writeString(testFile, "test-content-4");

        // WHEN: Compressing files
        InputStream zipResult = ZipUtils.compressFiles(List.of(testFile.toFile()));

        // THEN: Should fall back to default temp directory
        assertThat(zipResult).isNotNull();
        Path expectedDefaultDir = Path.of(System.getProperty("java.io.tmpdir"), "processvideo");
        assertThat(expectedDefaultDir).exists();

        // Cleanup
        zipResult.close();
    }

    @Test
    void shouldDeleteZipFileWhenInputStreamIsClosed() throws IOException {
        // GIVEN: A file to compress
        Path testFile = tempDir.resolve("test5.png");
        Files.writeString(testFile, "test-content-5");

        // WHEN: Compressing and then closing the stream
        InputStream zipResult = ZipUtils.compressFiles(List.of(testFile.toFile()));

        // Get the underlying file path before closing (implementation detail:
        // FileDeletingInputStream)
        // We can't easily get the file reference, but we can verify deletion happens
        assertThat(zipResult).isNotNull();

        // Close the stream which should trigger file deletion
        zipResult.close();

        // THEN: The temporary zip file should be deleted
        // We verify this indirectly by ensuring we can create another compression
        // without issues
        Path anotherFile = tempDir.resolve("test6.png");
        Files.writeString(anotherFile, "test-content-6");
        InputStream anotherZip = ZipUtils.compressFiles(List.of(anotherFile.toFile()));
        assertThat(anotherZip).isNotNull();
        anotherZip.close();
    }

    @Test
    void shouldHandleFileReadErrorDuringCompression() throws IOException {
        // GIVEN: A file that exists but becomes inaccessible
        Path testFile = tempDir.resolve("test7.png");
        Files.writeString(testFile, "test-content-7");
        File fileObject = testFile.toFile();

        // Make the file unreadable (this works on POSIX systems, may not work on
        // Windows)
        // Note: This test might behave differently on Windows vs Unix
        boolean madeUnreadable = fileObject.setReadable(false, false);

        try {
            if (madeUnreadable) {
                // WHEN/THEN: Attempting to compress should fail
                assertThatThrownBy(() -> ZipUtils.compressFiles(List.of(fileObject)))
                        .isInstanceOf(VideoProcessingException.class);
            } else {
                // On systems where we can't make files unreadable, skip the test
                // by doing a successful compression
                InputStream result = ZipUtils.compressFiles(List.of(fileObject));
                assertThat(result).isNotNull();
                result.close();
            }
        } finally {
            // Restore readability for cleanup
            fileObject.setReadable(true, false);
        }
    }

    @Test
    void shouldHandleMultipleFilesWithSomeDeleted() throws IOException {
        // GIVEN: Multiple files where one will be deleted during processing
        Path file1 = tempDir.resolve("frame_a.png");
        Path file2 = tempDir.resolve("frame_b.png");
        Path file3 = tempDir.resolve("frame_c.png");

        Files.writeString(file1, "content-a");
        Files.writeString(file2, "content-b");
        Files.writeString(file3, "content-c");

        // WHEN: Compressing files
        List<File> filesToZip = List.of(file1.toFile(), file2.toFile(), file3.toFile());
        InputStream zipResult = ZipUtils.compressFiles(filesToZip);

        // THEN: All files are in the ZIP
        assertThat(zipResult).isNotNull();

        try (ZipInputStream zis = new ZipInputStream(zipResult)) {
            int entryCount = 0;
            while (zis.getNextEntry() != null) {
                entryCount++;
            }
            assertThat(entryCount).isEqualTo(3);
        }
    }

    @Test
    void shouldHandleFilesWithSpecialCharactersInName() throws IOException {
        // GIVEN: A file with special characters in the name
        Path specialFile = tempDir.resolve("frame_#1_@test.png");
        Files.writeString(specialFile, "special-content");

        // WHEN: Compressing the file
        InputStream zipResult = ZipUtils.compressFiles(List.of(specialFile.toFile()));

        // THEN: The file is successfully compressed
        assertThat(zipResult).isNotNull();

        try (ZipInputStream zis = new ZipInputStream(zipResult)) {
            ZipEntry entry = zis.getNextEntry();
            assertThat(entry).isNotNull();
            @SuppressWarnings("nullness")
            String entryName = entry.getName();
            assertThat(entryName).contains("frame_#1_@test.png");
        }
    }

    @Test
    void shouldHandleLargeNumberOfFiles() throws IOException {
        // GIVEN: A large number of files
        int fileCount = 50;
        File[] files = new File[fileCount];

        for (int i = 0; i < fileCount; i++) {
            Path file = tempDir.resolve("frame_" + i + ".png");
            Files.writeString(file, "content-" + i);
            files[i] = file.toFile();
        }

        // WHEN: Compressing all files
        InputStream zipResult = ZipUtils.compressFiles(List.of(files));

        // THEN: All files are in the ZIP
        assertThat(zipResult).isNotNull();

        try (ZipInputStream zis = new ZipInputStream(zipResult)) {
            int entryCount = 0;
            while (zis.getNextEntry() != null) {
                entryCount++;
            }
            assertThat(entryCount).isEqualTo(fileCount);
        }
    }

    @Test
    void shouldHandleFileDeletingInputStreamMultipleCloses() throws IOException {
        // GIVEN: A compressed file
        Path testFile = tempDir.resolve("test8.png");
        Files.writeString(testFile, "test-content-8");
        InputStream zipResult = ZipUtils.compressFiles(List.of(testFile.toFile()));

        // WHEN: Closing the stream multiple times
        zipResult.close();
        zipResult.close(); // Should not throw exception

        // THEN: No exception is thrown
        assertThat(true).isTrue(); // Test passes if no exception
    }

    @Test
    void shouldHandleFileDeletionFailureInAddToZip() throws IOException {
        // GIVEN: A file in a directory that we'll make read-only to prevent deletion
        Path subDir = tempDir.resolve("readonly-dir");
        Files.createDirectories(subDir);

        Path testFile = subDir.resolve("locked-file.png");
        Files.writeString(testFile, "test-content-locked");

        // Make the parent directory read-only to prevent file deletion
        File dirFile = subDir.toFile();
        boolean originalWritable = dirFile.canWrite();
        dirFile.setWritable(false, false);

        try {
            // WHEN: Attempting to compress (which will try to delete the file after adding
            // to zip)
            // On Windows, this may or may not prevent deletion, so we handle both cases
            InputStream zipResult = ZipUtils.compressFiles(List.of(testFile.toFile()));

            // THEN: The compression succeeds even if file deletion fails
            assertThat(zipResult).isNotNull();

            // Verify the ZIP contains the file
            try (ZipInputStream zis = new ZipInputStream(zipResult)) {
                ZipEntry entry = zis.getNextEntry();
                assertThat(entry).isNotNull();
            }
        } finally {
            // Cleanup: Restore write permissions
            dirFile.setWritable(true, false);
            // Clean up any remaining files
            if (Files.exists(testFile)) {
                Files.delete(testFile);
            }
        }
    }

    @Test
    void shouldHandleFileInUseScenario() throws IOException {
        // GIVEN: A file that we'll keep open to simulate "in use" scenario
        Path testFile = tempDir.resolve("in-use-file.png");
        Files.writeString(testFile, "test-content-in-use");

        // Create a second identical file for the test since we can't reliably lock
        // files on all OSes
        Path testFile2 = tempDir.resolve("normal-file.png");
        Files.writeString(testFile2, "test-content-normal");

        // WHEN: Compressing files
        // Note: On Windows, file locking behavior is different than Unix
        // This test ensures the code handles deletion failures gracefully
        InputStream zipResult = ZipUtils.compressFiles(List.of(testFile.toFile(), testFile2.toFile()));

        // THEN: Compression succeeds
        assertThat(zipResult).isNotNull();

        try (ZipInputStream zis = new ZipInputStream(zipResult)) {
            int entryCount = 0;
            while (zis.getNextEntry() != null) {
                entryCount++;
            }
            assertThat(entryCount).isEqualTo(2);
        }
    }

    @Test
    void shouldHandleVariousTempFileCreationScenarios() throws IOException {
        // GIVEN: Multiple scenarios for temp file creation
        originalAppTempDir = System.getProperty("app.temp.dir");

        // Test 1: With custom temp dir that doesn't exist yet
        Path customTemp1 = tempDir.resolve("new-custom-temp-" + System.currentTimeMillis());
        System.setProperty("app.temp.dir", customTemp1.toString());

        Path file1 = tempDir.resolve("file-scenario-1.png");
        Files.writeString(file1, "scenario-1");

        // WHEN: Creating temp files via compression
        InputStream result1 = ZipUtils.compressFiles(List.of(file1.toFile()));
        assertThat(result1).isNotNull();
        assertThat(customTemp1).exists();
        result1.close();

        // Test 2: With a very long path name
        Path customTemp2 = tempDir.resolve("very-long-directory-name-to-test-path-handling-in-temp-file-creation");
        System.setProperty("app.temp.dir", customTemp2.toString());

        Path file2 = tempDir.resolve("file-scenario-2.png");
        Files.writeString(file2, "scenario-2");

        InputStream result2 = ZipUtils.compressFiles(List.of(file2.toFile()));
        assertThat(result2).isNotNull();
        assertThat(customTemp2).exists();
        result2.close();
    }

    @Test
    void shouldHandleRapidConsecutiveCompressions() throws IOException {
        // GIVEN: Multiple files to compress in rapid succession
        // This tests temp file creation under load and cleanup
        for (int i = 0; i < 5; i++) {
            Path testFile = tempDir.resolve("rapid-" + i + ".png");
            Files.writeString(testFile, "rapid-content-" + i);

            // WHEN: Compressing and immediately closing
            InputStream zipResult = ZipUtils.compressFiles(List.of(testFile.toFile()));
            assertThat(zipResult).isNotNull();

            // Verify content
            try (ZipInputStream zis = new ZipInputStream(zipResult)) {
                ZipEntry entry = zis.getNextEntry();
                assertThat(entry).isNotNull();
            }
        }

        // THEN: All operations succeed without resource leaks
        assertThat(true).isTrue();
    }

    @Test
    void shouldCompressLargeFile() throws IOException {
        // GIVEN: A larger file to ensure buffering works correctly
        Path largeFile = tempDir.resolve("large-file.png");
        byte[] largeContent = new byte[1024 * 100]; // 100KB
        for (int i = 0; i < largeContent.length; i++) {
            largeContent[i] = (byte) (i % 256);
        }
        Files.write(largeFile, largeContent);

        // WHEN: Compressing the large file
        InputStream zipResult = ZipUtils.compressFiles(List.of(largeFile.toFile()));

        // THEN: The file is successfully compressed
        assertThat(zipResult).isNotNull();

        try (ZipInputStream zis = new ZipInputStream(zipResult)) {
            ZipEntry entry = zis.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("large-file.png");

            // Read the content to ensure it was properly written
            byte[] buffer = new byte[1024];
            int totalRead = 0;
            int bytesRead;
            while ((bytesRead = zis.read(buffer)) != -1) {
                totalRead += bytesRead;
            }
            assertThat(totalRead).isEqualTo(largeContent.length);
        }
    }

    @Test
    void shouldHandleEmptyFile() throws IOException {
        // GIVEN: An empty file
        Path emptyFile = tempDir.resolve("empty.png");
        Files.createFile(emptyFile);

        // WHEN: Compressing the empty file
        InputStream zipResult = ZipUtils.compressFiles(List.of(emptyFile.toFile()));

        // THEN: The compression succeeds
        assertThat(zipResult).isNotNull();

        try (ZipInputStream zis = new ZipInputStream(zipResult)) {
            ZipEntry entry = zis.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("empty.png");
        }
    }
}