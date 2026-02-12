# Process Video Microservice

This microservice processes video files uploaded to an AWS S3 bucket. It extracts frames from the video, compresses them into a ZIP archive, and uploads the result to an output bucket. The service also sends status updates via AWS SQS throughout the process.

## Table of Contents
- [Architecture](#architecture)
- [Technologies](#technologies)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Usage](#usage)
- [Testing](#testing)

## Architecture

The project follows the **Hexagonal Architecture (Ports and Adapters)** pattern to ensure separation of concerns and testability.

- **Application:** Contains the application logic, input/output ports.
- **Domain:** Contains the core business logic (e.g., `VideoProcessorService`) and domain models.
- **Infra:** Contains adapters for external systems (AWS S3, SQS, LocalStack, etc.) and configuration.

## Technologies

- **Java 21**
- **Spring Boot 3** (Web, Actuator, Validation)
- **Spring Cloud AWS** (S3, SQS)
- **JCodec** (Video processing)
- **Docker & Docker Compose**
- **LocalStack** (AWS mocking for local development)
- **Terraform** (Infrastructure as Code)

## Prerequisites

- [Java 21](https://www.oracle.com/java/technologies/downloads/#java21)
- [Maven](https://maven.apache.org/download.cgi)
- [Docker](https://www.docker.com/products/docker-desktop)

## Getting Started

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/your-repo/process-video.git
    cd process-video
    ```

2.  **Build the project:**
    ```bash
    mvn clean install
    ```

3.  **Run with Docker Compose:**
    This command starts the application and a LocalStack container with the necessary AWS resources (S3 buckets, SQS queues).

    ```bash
    docker-compose up -d
    ```

    The application will be available at `http://localhost:8080`.
    LocalStack will be available at `http://localhost:4566`.

## Configuration

### Environment Variables (Local & Docker)
Key environment variables can be configured in `docker-compose.yml` or your `.env` file:

| Variable | Description | Default |
| :--- | :--- | :--- |
| `AWS_REGION` | AWS Region | `us-east-1` |
| `APP_BUCKETS_VIDEO_INPUT_STORAGE` | S3 Bucket for input videos | `video-input-storage` |
| `APP_BUCKETS_VIDEO_PROCESSED_STORAGE` | S3 Bucket for processed ZIPs | `video-processed-storage` |
| `SQS_VIDEO_PROCESS_COMMAND_URL` | SQS Queue URL for processing commands | (LocalStack URL) |
| `SQS_VIDEO_UPDATED_EVENT_URL` | SQS Queue URL for status updates | (LocalStack URL) |

### AWS SSM Parameter Store
For the application and infrastructure to function correctly in production (and for the GitHub Actions workflow), the following parameters must be created in AWS Systems Manager Parameter Store:

| Parameter Name | Description | Example Value |
| :--- | :--- | :--- |
| `/process-video/terraform/aws-account-id` | AWS Account ID used by Terraform | `123456789012` |
| `/process-video/aws/access-key-id` | AWS Access Key ID for the application | `AKIA...` |
| `/process-video/aws/secret-access-key` | AWS Secret Access Key for the application | `Secret...` |
| `/process-video/sqs/video-process-command-queue-name` | Name of the process command queue | `video-process-command` |
| `/process-video/sqs/video-updated-event-queue-name` | Name of the status update queue | `video-updated-event` |

> **Note:** The application requires `SQS_VIDEO_PROCESS_COMMAND_URL` and `SQS_VIDEO_UPDATED_EVENT_URL` environment variables. Ensure your deployment configuration (ExternalSecrets/ConfigMap) correctly maps these, or that your application can resolve URLs from the queue names provided in SSM.

### GitHub Actions Workflows
To run the CI/CD pipelines (`.github/workflows`), you need to configure the following in your GitHub Repository settings:

#### Secrets
| Secret Name | Description |
| :--- | :--- |
| `AWS_ACCOUNT_ID` | Your AWS Account ID. |
| `SONAR_TOKEN` | Token for SonarCloud analysis. |
| `GITHUB_TOKEN` | Automatically provided by GitHub, but ensure permissions are set. |

#### Variables (or Env Vars in Workflow)
The following are defined in `.github/workflows/cd-main.yml` but arguably should be repository variables if you need to change them:
- `AWS_REGION` (default: `us-east-1`)
- `CLUSTER_NAME` (default: `nextime-cluster`)
- `ECR_REPOSITORY` (default: `process-video`)

## Usage

This service operates asynchronously based on SQS messages.

1.  **Upload a video** to the input S3 bucket (`video-input-storage`).
2.  **Send a message** to the processing queue (`video-process-command`) with the S3 key of the uploaded video.
3.  The service will:
    - Download the video.
    - Extract frames.
    - Create a ZIP file of the frames.
    - Upload the ZIP to the output bucket (`video-processed-storage`).
    - Delete the source video.
    - Send status updates to the event queue (`video-updated-event`).

## Testing

To run the unit and integration tests:

```bash
mvn test
```
