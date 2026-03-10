package com.hackathon.processvideo.infra.adapter.inbound.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hackathon.processvideo.domain.model.enums.ProcessStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EventVideoUpdate")
class EventVideoUpdateTest {

    @Nested
    @DisplayName("Builder — happy path")
    class HappyPathTests {

        @Test
        @DisplayName("Should build successfully with all valid fields")
        void shouldBuildWithAllValidFields() {
            // Act
            final EventVideoUpdate event = EventVideoUpdate.builder()
                    .status(ProcessStatus.PROCESSING)
                    .keyName("videos/sample.mp4")
                    .bucketName("my-bucket")
                    .build();

            // Assert
            assertThat(event.getStatus()).isEqualTo(ProcessStatus.PROCESSING);
            assertThat(event.getKeyName()).isEqualTo("videos/sample.mp4");
            assertThat(event.getBucketName()).isEqualTo("my-bucket");
        }
    }

    @Nested
    @DisplayName("Builder — validation failures")
    class ValidationFailureTests {

        @Test
        @DisplayName("Should throw when status is null")
        void shouldThrowWhenStatusIsNull() {
            // Arrange
            final EventVideoUpdate.EventVideoUpdateBuilder builder = EventVideoUpdate.builder()
                    .status(null)
                    .keyName("videos/sample.mp4")
                    .bucketName("my-bucket");

            // Act / Assert
            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Status is mandatory");
        }

        @Test
        @DisplayName("Should throw when keyName is blank")
        void shouldThrowWhenKeyNameIsBlank() {
            // Arrange
            final EventVideoUpdate.EventVideoUpdateBuilder builder = EventVideoUpdate.builder()
                    .status(ProcessStatus.FAILED)
                    .keyName("")
                    .bucketName("my-bucket");

            // Act / Assert
            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Key name is mandatory");
        }

        @Test
        @DisplayName("Should throw when keyName is null")
        void shouldThrowWhenKeyNameIsNull() {
            // Arrange
            final EventVideoUpdate.EventVideoUpdateBuilder builder = EventVideoUpdate.builder()
                    .status(ProcessStatus.FAILED)
                    .keyName(null)
                    .bucketName("my-bucket");

            // Act / Assert
            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Key name is mandatory");
        }

        @Test
        @DisplayName("Should throw when bucketName is blank")
        void shouldThrowWhenBucketNameIsBlank() {
            // Arrange
            final EventVideoUpdate.EventVideoUpdateBuilder builder = EventVideoUpdate.builder()
                    .status(ProcessStatus.PROCESSED)
                    .keyName("videos/sample.mp4")
                    .bucketName("");

            // Act / Assert
            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Bucket name is mandatory");
        }

        @Test
        @DisplayName("Should throw when bucketName is null")
        void shouldThrowWhenBucketNameIsNull() {
            // Arrange
            final EventVideoUpdate.EventVideoUpdateBuilder builder = EventVideoUpdate.builder()
                    .status(ProcessStatus.PROCESSED)
                    .keyName("videos/sample.mp4")
                    .bucketName(null);

            // Act / Assert
            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Bucket name is mandatory");
        }
    }

    @Nested
    @DisplayName("No-args constructor")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("Should create instance with null fields via no-args constructor")
        void shouldCreateInstanceWithNoArgs() {
            // Act
            final EventVideoUpdate event = new EventVideoUpdate();

            // Assert
            assertThat(event.getStatus()).isNull();
            assertThat(event.getKeyName()).isNull();
            assertThat(event.getBucketName()).isNull();
        }
    }
}
