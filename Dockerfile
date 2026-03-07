# ----- BUILD PHASE -----
FROM amazoncorretto:21-alpine AS build

WORKDIR /app

RUN apk add --no-cache maven

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests


# ----- RUNTIME PHASE -----
FROM amazoncorretto:21-alpine

# Install curl (needed for healthcheck + LocalStack readiness wait)
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

RUN chown -R appuser:appgroup /app

COPY --from=build /app/target/*.jar app.jar
RUN chown appuser:appgroup /app/app.jar

# Switch to non-root user
USER appuser

# Wait for LocalStack init scripts to complete before starting,
# then launch Spring Boot. The /_localstack/init endpoint reports
# FINISHED once all ready.d scripts have run (queues/buckets created).
ENTRYPOINT ["sh", "-c", "\
    echo 'Waiting for LocalStack init scripts to finish...' && \
    until curl -sf http://localstack:4566/_localstack/init | grep -q '\"READY\": true'; do \
    echo '  LocalStack init not ready yet, retrying in 3s...' && sleep 3; \
    done && \
    echo 'LocalStack init READY — starting process-video' && \
    exec java -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-dev} -jar app.jar \
    "]
