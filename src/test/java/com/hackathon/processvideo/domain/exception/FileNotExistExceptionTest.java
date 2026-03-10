package com.hackathon.processvideo.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FileNotExistException")
class FileNotExistExceptionTest {

    @Nested
    @DisplayName("Constructor with message only")
    class MessageOnlyConstructorTests {

        @Test
        @DisplayName("Should create exception with correct message")
        void shouldCreateExceptionWithMessage() {
            // Arrange
            final String message = "File does not exist";

            // Act
            final FileNotExistException exception = new FileNotExistException(message);

            // Assert
            assertThat(exception.getMessage()).isEqualTo(message);
            assertThat(exception.getCause()).isNull();
        }
    }

    @Nested
    @DisplayName("Constructor with message and cause")
    class MessageAndCauseConstructorTests {

        @Test
        @DisplayName("Should create exception with correct message and cause")
        void shouldCreateExceptionWithMessageAndCause() {
            // Arrange
            final String message = "File does not exist";
            final Throwable cause = new RuntimeException("underlying IO error");

            // Act
            final FileNotExistException exception = new FileNotExistException(message, cause);

            // Assert
            assertThat(exception.getMessage()).isEqualTo(message);
            assertThat(exception.getCause()).isSameAs(cause);
        }

        @Test
        @DisplayName("Should be instance of VideoProcessingException")
        void shouldExtendVideoProcessingException() {
            // Act
            final FileNotExistException exception = new FileNotExistException("msg", new RuntimeException());

            // Assert
            assertThat(exception).isInstanceOf(VideoProcessingException.class);
        }
    }
}
