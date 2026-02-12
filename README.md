# Process Video Microservice

A cloud-native microservice for automated video processing, built with clean architecture principles and AWS
infrastructure.

## Overview

This microservice provides asynchronous video processing capabilities through event-driven architecture. It
automatically extracts frames from uploaded videos, packages them efficiently, and manages the complete lifecycle
through distributed messaging patterns.

**Key Features:**

- **Event-Driven Architecture:** Asynchronous processing with message-based coordination
- **Cloud-Native Design:** Scalable, resilient infrastructure leveraging AWS services
- **Clean Architecture:** Separation of concerns through hexagonal pattern
- **Developer Experience:** Local development environment with cloud service emulation
- **Production Ready:** Automated deployment pipelines and infrastructure provisioning

## 📋 Table of Contents

- [Architecture](#architecture)
- [Technologies](#technologies)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Usage](#usage)
- [Testing](#testing)

## 🏗️ Architecture

The project implements **Hexagonal Architecture (Ports and Adapters)** to ensure separation of concerns, testability,
and independence from external frameworks.

```
├── Application    → Use cases and orchestration logic
├── Domain        → Core business rules and models
└── Infrastructure → External system integrations
```

**Processing Flow:**

1. Upload triggers processing workflow
2. Service orchestrates frame extraction pipeline
3. Results are packaged and stored
4. Lifecycle events are published for monitoring
5. Cleanup operations complete the workflow

## 🛠️ Technologies

- **Java 21** - Modern JVM platform
- **Spring Boot 3** - Application framework with cloud integrations
- **Spring Cloud AWS** - Native cloud service bindings
- **JCodec** - Media processing capabilities
- **Docker & Docker Compose** - Container orchestration
- **LocalStack** - Local cloud environment
- **Terraform** - Infrastructure as Code
- **GitHub Actions** - CI/CD automation
- **SonarCloud** - Code quality analysis

## 📦 Prerequisites

- [Java 21](https://www.oracle.com/java/technologies/downloads/#java21)
- [Maven 3.8+](https://maven.apache.org/download.cgi)
- [Docker](https://www.docker.com/products/docker-desktop) and Docker Compose

## 🚀 Getting Started

1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-repo/process-video.git
   cd process-video
   ```

2. **Build the project:**
   ```bash
   mvn clean install
   ```

3. **Run with Docker Compose:**
   ```bash
   docker-compose up -d
   ```

    - Application: `http://localhost:8080`
    - Local Cloud Services: `http://localhost:4566`
    - Health Check: `http://localhost:8080/actuator/health`

## ⚙️ Configuration

### Environment Variables

Configure runtime behavior through environment variables:

| Variable | Description | Default |
| :--- | :--- | :--- |
| `AWS_REGION` | Cloud region | `us-east-1` |
| `APP_BUCKETS_VIDEO_INPUT_STORAGE` | Input storage location | `video-input-storage` |
| `APP_BUCKETS_VIDEO_PROCESSED_STORAGE` | Output storage location | `video-processed-storage` |
| `SQS_VIDEO_PROCESS_COMMAND_URL` | Command queue endpoint | (Local URL) |
| `SQS_VIDEO_UPDATED_EVENT_URL` | Event queue endpoint | (Local URL) |

### Cloud Parameter Store

Production deployments require these parameters in AWS Systems Manager:

| Parameter Name | Description |
| :--- | :--- |
| `/process-video/terraform/aws-account-id` | Cloud account identifier |
| `/process-video/aws/access-key-id` | Service credentials |
| `/process-video/aws/secret-access-key` | Service credentials |
| `/process-video/sqs/video-process-command-queue-name` | Command queue identifier |
| `/process-video/sqs/video-updated-event-queue-name` | Event queue identifier |

### CI/CD Configuration

Configure deployment automation through repository secrets:

| Secret Name | Description |
| :--- | :--- |
| `AWS_ACCOUNT_ID` | Cloud account identifier |
| `SONAR_TOKEN` | Code analysis credentials |
| `GITHUB_TOKEN` | Automation token |

## 📖 Usage

The service processes requests through asynchronous messaging:

1. **Submit a processing request** by uploading content to the input storage
2. **Send a command message** with the resource identifier
3. **The service orchestrates** the complete processing workflow
4. **Monitor progress** through published lifecycle events
5. **Retrieve results** from the output storage location

Example command message structure:

```json
{
  "videoKey": "resource-identifier",
  "requestId": "correlation-id"
}
```

## 🧪 Testing

Execute the test suite:

```bash
mvn test
```

Generate coverage reports:

```bash
mvn clean verify
```

The project includes comprehensive testing at multiple levels:

- Unit tests for business logic
- Integration tests with infrastructure
- End-to-end workflow validation

 