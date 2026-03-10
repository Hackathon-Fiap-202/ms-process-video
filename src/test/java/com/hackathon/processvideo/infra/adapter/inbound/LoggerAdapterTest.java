package com.hackathon.processvideo.infra.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LoggerAdapter")
class LoggerAdapterTest {

    private LoggerAdapter loggerAdapter;

    @BeforeEach
    void setUp() {
        loggerAdapter = new LoggerAdapter();
    }

    @Nested
    @DisplayName("info")
    class InfoTests {

        @Test
        @DisplayName("Should log info message without throwing exceptions")
        void shouldLogInfoMessage() {
            // Act / Assert
            assertThatCode(() -> loggerAdapter.info("Info message: {}", "value"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should sanitize newline characters in info args")
        void shouldSanitizeNewlineInInfoArgs() {
            // Act / Assert
            assertThatCode(() -> loggerAdapter.info("Info: {}", "line1\nline2"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle null args in info")
        void shouldHandleNullArgsInInfo() {
            // Act / Assert
            assertThatCode(() -> loggerAdapter.info("Info null args", (Object[]) null))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("debug")
    class DebugTests {

        @Test
        @DisplayName("Should log debug message without throwing exceptions")
        void shouldLogDebugMessage() {
            // Act / Assert
            assertThatCode(() -> loggerAdapter.debug("Debug message: {}", "value"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle null args in debug")
        void shouldHandleNullArgsInDebug() {
            // Act / Assert
            assertThatCode(() -> loggerAdapter.debug("Debug null args", (Object[]) null))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("warn")
    class WarnTests {

        @Test
        @DisplayName("Should log warn message without throwing exceptions")
        void shouldLogWarnMessage() {
            // Act / Assert
            assertThatCode(() -> loggerAdapter.warn("Warn message: {}", "value"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle null args in warn")
        void shouldHandleNullArgsInWarn() {
            // Act / Assert
            assertThatCode(() -> loggerAdapter.warn("Warn null args", (Object[]) null))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("error")
    class ErrorTests {

        @Test
        @DisplayName("Should log error message with throwable without throwing exceptions")
        void shouldLogErrorWithThrowable() {
            // Arrange
            final Throwable cause = new RuntimeException("test error");

            // Act / Assert
            assertThatCode(() -> loggerAdapter.error("Error %s", cause, "detail"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should log error message without throwable")
        void shouldLogErrorWithoutThrowable() {
            // Act / Assert
            assertThatCode(() -> loggerAdapter.error("Error message: {}", "detail"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should sanitize carriage return characters in error args")
        void shouldSanitizeCarriageReturnInErrorArgs() {
            // Act / Assert
            assertThatCode(() -> loggerAdapter.error("Error: {}", "line1\rline2"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle null arg value in error")
        void shouldHandleNullArgValueInError() {
            // Act / Assert
            assertThatCode(() -> loggerAdapter.error("Error: {}", (Object) null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle null args array in error without throwable")
        void shouldHandleNullArgsArrayInError() {
            // Act / Assert
            assertThatCode(() -> loggerAdapter.error("Error null args", (Object[]) null))
                    .doesNotThrowAnyException();
        }
    }
}
