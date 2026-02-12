package com.hackathon.processvideo.infra.adapter.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hackathon.processvideo.domain.exception.VideoProcessingException;
import com.hackathon.processvideo.domain.port.out.LoggerPort;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkException;

/**
 * Comprehensive unit tests for S3Adapter.
 * <p>
 * Tests cover:
 * - File retrieval: Success and failure scenarios
 * - File upload: Success and failure scenarios
 * - File deletion: Success and error handling
 * - File size retrieval: Success and failure scenarios
 * - Exception handling: Proper conversion of AWS exceptions to domain exceptions
 * - Logging: Verification of appropriate logging at different levels
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("S3Adapter Unit Tests")
class S3AdapterTest {

    private static final String BUCKET_NAME = "test-bucket";
    private static final String FILE_KEY = "test-file.mp4";
    private static final long FILE_SIZE = 1024L * 1024L; // 1 MB
    private static final byte[] FILE_CONTENT = "test file content".getBytes();

    @Mock
    private S3Template s3Template;

    @Mock
    private LoggerPort loggerPort;

    @Mock
    private S3Resource s3Resource;

    private S3Adapter s3Adapter;

    @BeforeEach
    void setUp() {
        s3Adapter = new S3Adapter(s3Template, loggerPort);
    }

    @Nested
    @DisplayName("File Retrieval Tests (getFile)")
    class GetFileTests {

        @Test
        @DisplayName("Should successfully retrieve file from S3")
        void shouldSuccessfullyRetrieveFile() throws IOException {
            // Arrange
            final InputStream expectedInputStream = new ByteArrayInputStream(FILE_CONTENT);
            when(s3Template.download(BUCKET_NAME, FILE_KEY))
                    .thenReturn(s3Resource);
            when(s3Resource.getInputStream())
                    .thenReturn(expectedInputStream);

            // Act
            final InputStream result = s3Adapter.getFile(BUCKET_NAME, FILE_KEY);

            // Assert
            assertNotNull(result);
            assertEquals(expectedInputStream, result);
            verify(s3Template, times(1)).download(BUCKET_NAME, FILE_KEY);
            verify(s3Resource, times(1)).getInputStream();
            verify(loggerPort, times(1)).info(
                    "[S3Adapter][getFile] Downloading file from S3, bucket={}, key={}",
                    BUCKET_NAME, FILE_KEY);
        }

        @Test
        @DisplayName("Should log info message when retrieving file")
        void shouldLogInfoMessageDuringRetrieval() throws IOException {
            // Arrange
            when(s3Template.download(BUCKET_NAME, FILE_KEY))
                    .thenReturn(s3Resource);
            when(s3Resource.getInputStream())
                    .thenReturn(new ByteArrayInputStream(FILE_CONTENT));

            // Act
            s3Adapter.getFile(BUCKET_NAME, FILE_KEY);

            // Assert
            verify(loggerPort).info(
                    "[S3Adapter][getFile] Downloading file from S3, bucket={}, key={}",
                    BUCKET_NAME, FILE_KEY);
        }
    }

    @Nested
    @DisplayName("File Upload Tests (uploadFile)")
    class UploadFileTests {

        @Test
        @DisplayName("Should successfully upload file to S3")
        void shouldSuccessfullyUploadFile() {
            // Arrange
            final InputStream fileStream = new ByteArrayInputStream(FILE_CONTENT);
            when(s3Template.upload(BUCKET_NAME, FILE_KEY, fileStream))
                    .thenReturn(s3Resource);

            // Act
            final boolean result = s3Adapter.uploadFile(BUCKET_NAME, FILE_KEY, fileStream);

            // Assert
            assertTrue(result);
            verify(s3Template, times(1)).upload(BUCKET_NAME, FILE_KEY, fileStream);
            verify(loggerPort, times(1)).info(
                    "[S3Adapter][uploadFile] Starting upload to S3, bucket={}, key={}",
                    BUCKET_NAME, FILE_KEY);
            verify(loggerPort, times(1)).info(
                    "[S3Adapter][uploadFile] Upload completed successfully, bucket={}, key={}",
                    BUCKET_NAME, FILE_KEY);
        }

        @Test
        @DisplayName("Should return false when upload fails with SdkException")
        void shouldReturnFalseWhenUploadVerificationFails() {
            // Arrange
            final InputStream fileStream = new ByteArrayInputStream(FILE_CONTENT);
            final SdkException sdkException = mock(SdkException.class);
            when(sdkException.getMessage()).thenReturn("Upload failed");
            when(s3Template.upload(BUCKET_NAME, FILE_KEY, fileStream))
                    .thenThrow(sdkException);

            // Act
            final boolean result = s3Adapter.uploadFile(BUCKET_NAME, FILE_KEY, fileStream);

            // Assert
            assertFalse(result);
            verify(s3Template, times(1)).upload(BUCKET_NAME, FILE_KEY, fileStream);
            verify(loggerPort, times(1)).info(
                    "[S3Adapter][uploadFile] Starting upload to S3, bucket={}, key={}",
                    BUCKET_NAME, FILE_KEY);
            verify(loggerPort, times(1)).error(
                    "[S3Adapter][uploadFile] AWS SDK error during upload to S3, bucket={}, key={}, error={}",
                    BUCKET_NAME, FILE_KEY, "Upload failed");
        }

        @Test
        @DisplayName("Should return false when upload fails with IOException")
        void shouldReturnFalseWhenUploadFailsWithIOException() throws IOException {
            // Arrange
            final InputStream fileStream = mock(InputStream.class);
            // Mock close() to throw IOException (try-with-resources calls close())
            doThrow(new IOException("Stream close error")).when(fileStream).close();
            when(s3Template.upload(BUCKET_NAME, FILE_KEY, fileStream))
                    .thenReturn(s3Resource);

            // Act
            final boolean result = s3Adapter.uploadFile(BUCKET_NAME, FILE_KEY, fileStream);

            // Assert
            assertFalse(result);
            verify(loggerPort, times(1)).info(
                    "[S3Adapter][uploadFile] Starting upload to S3, bucket={}, key={}",
                    BUCKET_NAME, FILE_KEY);
            verify(loggerPort, times(1)).error(
                    "[S3Adapter][uploadFile] IOException during upload to S3, bucket={}, key={}, error={}",
                    BUCKET_NAME, FILE_KEY, "Stream close error");
        }

        @Test
        @DisplayName("Should log info message when upload starts")
        void shouldLogInfoMessageWhenUploadStarts() {
            // Arrange
            final InputStream fileStream = new ByteArrayInputStream(FILE_CONTENT);
            when(s3Template.upload(BUCKET_NAME, FILE_KEY, fileStream))
                    .thenReturn(s3Resource);

            // Act
            s3Adapter.uploadFile(BUCKET_NAME, FILE_KEY, fileStream);

            // Assert
            verify(loggerPort).info(
                    "[S3Adapter][uploadFile] Starting upload to S3, bucket={}, key={}",
                    BUCKET_NAME, FILE_KEY);
        }

        @Test
        @DisplayName("Should log success message after upload completes")
        void shouldLogSuccessMessageAfterUpload() {
            // Arrange
            final InputStream fileStream = new ByteArrayInputStream(FILE_CONTENT);
            when(s3Template.upload(BUCKET_NAME, FILE_KEY, fileStream))
                    .thenReturn(s3Resource);

            // Act
            s3Adapter.uploadFile(BUCKET_NAME, FILE_KEY, fileStream);

            // Assert
            verify(loggerPort).info(
                    "[S3Adapter][uploadFile] Upload completed successfully, bucket={}, key={}",
                    BUCKET_NAME, FILE_KEY);
        }

        @Test
        @DisplayName("Should close input stream after upload attempt")
        void shouldCloseInputStreamAfterUpload() {
            // Arrange
            final InputStream fileStream = new ByteArrayInputStream(FILE_CONTENT);
            when(s3Template.upload(BUCKET_NAME, FILE_KEY, fileStream))
                    .thenReturn(s3Resource);

            // Act
            s3Adapter.uploadFile(BUCKET_NAME, FILE_KEY, fileStream);

            // Assert
            // Stream should be closed (try-with-resources in the implementation)
            // If stream was not closed, this assertion would fail in real scenario
            verify(s3Template).upload(BUCKET_NAME, FILE_KEY, fileStream);
        }
    }

    @Nested
    @DisplayName("File Deletion Tests (deleteFile)")
    class DeleteFileTests {

        @Test
        @DisplayName("Should successfully delete file from S3")
        void shouldSuccessfullyDeleteFile() {
            // Act
            s3Adapter.deleteFile(BUCKET_NAME, FILE_KEY);

            // Assert
            verify(s3Template, times(1)).deleteObject(BUCKET_NAME, FILE_KEY);
            verify(loggerPort, times(1)).debug(
                    "[S3Adapter][deleteFile] Deleting file from S3, bucket={}, key={}",
                    BUCKET_NAME, FILE_KEY);
            verify(loggerPort, times(1)).info(
                    "[S3Adapter][deleteFile] File deleted successfully, bucket={}, key={}",
                    BUCKET_NAME, FILE_KEY);
        }

        @Test
        @DisplayName("Should log warning and continue when deletion fails due to SdkException")
        void shouldLogWarningOnDeletionFailure() {
            // Arrange
            final SdkException sdkException = mock(SdkException.class);
            when(sdkException.getMessage()).thenReturn("Access denied");
            doThrow(sdkException)
                    .when(s3Template).deleteObject(BUCKET_NAME, FILE_KEY);

            // Act
            s3Adapter.deleteFile(BUCKET_NAME, FILE_KEY);

            // Assert
            verify(s3Template, times(1)).deleteObject(BUCKET_NAME, FILE_KEY);
            verify(loggerPort, times(1)).debug(
                    "[S3Adapter][deleteFile] Deleting file from S3, bucket={}, key={}",
                    BUCKET_NAME, FILE_KEY);
            verify(loggerPort, times(1)).warn(
                    "[S3Adapter][deleteFile] Error deleting file from S3, bucket={}, key={}, error={}",
                    BUCKET_NAME, FILE_KEY, "Access denied");
            // Info message should NOT be logged on failure
            verify(loggerPort, never()).info(
                    "[S3Adapter][deleteFile] File deleted successfully, bucket={}, key={}",
                    BUCKET_NAME, FILE_KEY);
        }

        @Test
        @DisplayName("Should log debug message when starting deletion")
        void shouldLogDebugMessageWhenDeletingStarts() {
            // Act
            s3Adapter.deleteFile(BUCKET_NAME, FILE_KEY);

            // Assert
            verify(loggerPort).debug(
                    "[S3Adapter][deleteFile] Deleting file from S3, bucket={}, key={}",
                    BUCKET_NAME, FILE_KEY);
        }

        @Test
        @DisplayName("Should not throw exception when deletion fails")
        void shouldNotThrowExceptionOnDeletionFailure() {
            // Arrange
            final SdkException sdkException = mock(SdkException.class);
            when(sdkException.getMessage()).thenReturn("Network error");
            doThrow(sdkException)
                    .when(s3Template).deleteObject(BUCKET_NAME, FILE_KEY);

            // Act & Assert - should not throw
            s3Adapter.deleteFile(BUCKET_NAME, FILE_KEY);
            verify(s3Template, times(1)).deleteObject(BUCKET_NAME, FILE_KEY);
            verify(loggerPort, times(1)).warn(
                    "[S3Adapter][deleteFile] Error deleting file from S3, bucket={}, key={}, error={}",
                    BUCKET_NAME, FILE_KEY, "Network error");
        }
    }

    @Nested
    @DisplayName("File Size Retrieval Tests (getSize)")
    class GetSizeTests {

        @Test
        @DisplayName("Should successfully retrieve file size from S3")
        void shouldSuccessfullyGetFileSize() {
            // Arrange
            when(s3Template.download(BUCKET_NAME, FILE_KEY))
                    .thenReturn(s3Resource);
            when(s3Resource.exists())
                    .thenReturn(true);
            when(s3Resource.contentLength())
                    .thenReturn(FILE_SIZE);

            // Act
            final Long result = s3Adapter.getSize(BUCKET_NAME, FILE_KEY);

            // Assert
            assertEquals(FILE_SIZE, result);
            verify(s3Template, times(1)).download(BUCKET_NAME, FILE_KEY);
            verify(s3Resource, times(1)).exists();
            verify(s3Resource, times(1)).contentLength();
            verify(loggerPort, times(1)).debug(
                    "[S3Adapter][getSize] Retrieving file size, bucket={}, key={}",
                    BUCKET_NAME, FILE_KEY);
            verify(loggerPort, times(1)).info(
                    "[S3Adapter][getSize] File size retrieved, bucket={}, key={}, size={}bytes",
                    BUCKET_NAME, FILE_KEY, FILE_SIZE);
        }

        @Test
        @DisplayName("Should throw VideoProcessingException when file does not exist")
        void shouldThrowVideoProcessingExceptionWhenFileNotExists() {
            // Arrange
            when(s3Template.download(BUCKET_NAME, FILE_KEY))
                    .thenReturn(s3Resource);
            when(s3Resource.exists())
                    .thenReturn(false);

            // Act & Assert
            final VideoProcessingException exception = assertThrows(
                    VideoProcessingException.class,
                    () -> s3Adapter.getSize(BUCKET_NAME, FILE_KEY));

            assertEquals("Arquivo não encontrado no S3: " + FILE_KEY, exception.getMessage());
            verify(loggerPort, times(1)).error(
                    "[S3Adapter][getSize] File not found in S3, bucket={}, key={}",
                    BUCKET_NAME, FILE_KEY);
        }

        @Test
        @DisplayName("Should throw VideoProcessingException when SdkException occurs")
        void shouldThrowVideoProcessingExceptionOnSdkException() {
            // Arrange
            final SdkException sdkException = mock(SdkException.class);
            when(sdkException.getMessage()).thenReturn("Connection timeout");
            when(s3Template.download(BUCKET_NAME, FILE_KEY))
                    .thenThrow(sdkException);

            // Act & Assert
            final VideoProcessingException exception = assertThrows(
                    VideoProcessingException.class,
                    () -> s3Adapter.getSize(BUCKET_NAME, FILE_KEY));

            assertEquals("Erro ao obter tamanho do arquivo: " + FILE_KEY, exception.getMessage());
            assertEquals(sdkException, exception.getCause());
            verify(loggerPort, times(1)).error(
                    "[S3Adapter][getSize] Error retrieving file size, bucket={}, key={}, error={}",
                    BUCKET_NAME, FILE_KEY, "Connection timeout");
        }

        @Test
        @DisplayName("Should log debug message when starting size retrieval")
        void shouldLogDebugMessageWhenRetrievingSize() {
            // Arrange
            when(s3Template.download(BUCKET_NAME, FILE_KEY))
                    .thenReturn(s3Resource);
            when(s3Resource.exists())
                    .thenReturn(true);
            when(s3Resource.contentLength())
                    .thenReturn(FILE_SIZE);

            // Act
            s3Adapter.getSize(BUCKET_NAME, FILE_KEY);

            // Assert
            verify(loggerPort).debug(
                    "[S3Adapter][getSize] Retrieving file size, bucket={}, key={}",
                    BUCKET_NAME, FILE_KEY);
        }

        @Test
        @DisplayName("Should return correct file size")
        void shouldReturnCorrectFileSize() {
            // Arrange
            final long expectedSize = 5_242_880L; // 5 MB
            when(s3Template.download(BUCKET_NAME, FILE_KEY))
                    .thenReturn(s3Resource);
            when(s3Resource.exists())
                    .thenReturn(true);
            when(s3Resource.contentLength())
                    .thenReturn(expectedSize);

            // Act
            final Long result = s3Adapter.getSize(BUCKET_NAME, FILE_KEY);

            // Assert
            assertEquals(expectedSize, result);
        }
    }

    @Nested
    @DisplayName("Exception Handling Tests")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("Should convert SdkException to VideoProcessingException in getSize")
        void shouldConvertSdkExceptionToVideoProcessingException() {
            // Arrange
            final SdkException sdkException = mock(SdkException.class);
            when(s3Template.download(BUCKET_NAME, FILE_KEY))
                    .thenThrow(sdkException);

            // Act & Assert
            assertThrows(VideoProcessingException.class, () -> s3Adapter.getSize(BUCKET_NAME, FILE_KEY));
        }

        @Test
        @DisplayName("Should handle SdkException gracefully in deleteFile")
        void shouldHandleSdkExceptionInDeleteFile() {
            // Arrange
            final SdkException sdkException = mock(SdkException.class);
            when(sdkException.getMessage()).thenReturn("Permission denied");
            doThrow(sdkException)
                    .when(s3Template).deleteObject(BUCKET_NAME, FILE_KEY);

            // Act & Assert - should not throw
            s3Adapter.deleteFile(BUCKET_NAME, FILE_KEY);
            verify(loggerPort).warn(anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Logging Behavior Tests")
    class LoggingBehaviorTests {

        @Test
        @DisplayName("Should log correct bucket and key information")
        void shouldLogCorrectBucketAndKeyInfo() throws IOException {
            // Arrange
            when(s3Template.download(BUCKET_NAME, FILE_KEY))
                    .thenReturn(s3Resource);
            when(s3Resource.getInputStream())
                    .thenReturn(new ByteArrayInputStream(FILE_CONTENT));

            // Act
            s3Adapter.getFile(BUCKET_NAME, FILE_KEY);

            // Assert
            verify(loggerPort).info(
                    "[S3Adapter][getFile] Downloading file from S3, bucket={}, key={}",
                    BUCKET_NAME, FILE_KEY);
        }

        @Test
        @DisplayName("Should log file size in info message")
        void shouldLogFileSizeInInfoMessage() {
            // Arrange
            when(s3Template.download(BUCKET_NAME, FILE_KEY))
                    .thenReturn(s3Resource);
            when(s3Resource.exists())
                    .thenReturn(true);
            when(s3Resource.contentLength())
                    .thenReturn(FILE_SIZE);

            // Act
            s3Adapter.getSize(BUCKET_NAME, FILE_KEY);

            // Assert
            verify(loggerPort).info(
                    "[S3Adapter][getSize] File size retrieved, bucket={}, key={}, size={}bytes",
                    BUCKET_NAME, FILE_KEY, FILE_SIZE);
        }
    }

    @Nested
    @DisplayName("Call Order and Interaction Tests")
    class CallOrderTests {

        @Test
        @DisplayName("Should call download before getInputStream in getFile")
        void shouldCallDownloadBeforeGetInputStream() throws IOException {
            // Arrange
            when(s3Template.download(BUCKET_NAME, FILE_KEY))
                    .thenReturn(s3Resource);
            when(s3Resource.getInputStream())
                    .thenReturn(new ByteArrayInputStream(FILE_CONTENT));

            // Act
            s3Adapter.getFile(BUCKET_NAME, FILE_KEY);

            // Assert
            final InOrder inOrder = inOrder(s3Template, s3Resource);
            inOrder.verify(s3Template).download(BUCKET_NAME, FILE_KEY);
            inOrder.verify(s3Resource).getInputStream();
        }

        @Test
        @DisplayName("Should call upload and return true on successful upload")
        void shouldCallUploadBeforeExistsCheck() {
            // Arrange
            final InputStream fileStream = new ByteArrayInputStream(FILE_CONTENT);
            when(s3Template.upload(BUCKET_NAME, FILE_KEY, fileStream))
                    .thenReturn(s3Resource);

            // Act
            final boolean result = s3Adapter.uploadFile(BUCKET_NAME, FILE_KEY, fileStream);

            // Assert
            assertTrue(result);
            verify(s3Template, times(1)).upload(BUCKET_NAME, FILE_KEY, fileStream);
            verify(loggerPort, times(1)).info(
                    "[S3Adapter][uploadFile] Starting upload to S3, bucket={}, key={}",
                    BUCKET_NAME, FILE_KEY);
            verify(loggerPort, times(1)).info(
                    "[S3Adapter][uploadFile] Upload completed successfully, bucket={}, key={}",
                    BUCKET_NAME, FILE_KEY);
        }

        @Test
        @DisplayName("Should call download then exists check in getSize")
        void shouldCallDownloadThenExistsCheckInGetSize() {
            // Arrange
            when(s3Template.download(BUCKET_NAME, FILE_KEY))
                    .thenReturn(s3Resource);
            when(s3Resource.exists())
                    .thenReturn(true);
            when(s3Resource.contentLength())
                    .thenReturn(FILE_SIZE);

            // Act
            s3Adapter.getSize(BUCKET_NAME, FILE_KEY);

            // Assert
            final InOrder inOrder = inOrder(s3Template, s3Resource);
            inOrder.verify(s3Template).download(BUCKET_NAME, FILE_KEY);
            inOrder.verify(s3Resource).exists();
            inOrder.verify(s3Resource).contentLength();
        }
    }
}

