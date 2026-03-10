package com.hackathon.processvideo.infra.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MessagePublishException")
class MessagePublishExceptionTest {

    @Nested
    @DisplayName("Constructor with message and cause")
    class MessageAndCauseConstructorTests {

        @Test
        @DisplayName("Should create exception with correct message and cause")
        void shouldCreateExceptionWithMessageAndCause() {
            // Arrange
            final String message = "Failed to publish message to SQS";
            final Exception cause = new RuntimeException("SQS unavailable");

            // Act
            final MessagePublishException exception = new MessagePublishException(message, cause);

            // Assert
            assertThat(exception.getMessage()).isEqualTo(message);
            assertThat(exception.getCause()).isSameAs(cause);
        }
    }

    @Nested
    @DisplayName("Constructor with message only")
    class MessageOnlyConstructorTests {

        @Test
        @DisplayName("Should create exception with correct message and no cause")
        void shouldCreateExceptionWithMessageOnly() {
            // Arrange
            final String message = "Failed to publish message to SQS";

            // Act
            final MessagePublishException exception = new MessagePublishException(message);

            // Assert
            assertThat(exception.getMessage()).isEqualTo(message);
            assertThat(exception.getCause()).isNull();
        }

        @Test
        @DisplayName("Should extend RuntimeException")
        void shouldExtendRuntimeException() {
            // Act
            final MessagePublishException exception = new MessagePublishException("msg");

            // Assert
            assertThat(exception).isInstanceOf(RuntimeException.class);
        }
    }
}
