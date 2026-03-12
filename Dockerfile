# ----- BUILD PHASE -----
FROM amazoncorretto:21-alpine AS build

WORKDIR /app

RUN apk add --no-cache curl maven

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

# Download Datadog Java agent during build so the runtime image has no extra tools
RUN curl -Lo /app/dd-java-agent.jar https://dtdg.co/latest-java-tracer


# ----- RUNTIME PHASE -----
FROM amazoncorretto:21-alpine

# Install curl since it is needed by the ECS Task Definition health check
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

RUN chown -R appuser:appgroup /app

COPY --from=build /app/target/*.jar app.jar
# Datadog Java agent — auto-instruments Spring Boot, SQS, S3, HTTP clients
COPY --from=build /app/dd-java-agent.jar /dd-java-agent.jar
RUN chown appuser:appgroup /app/app.jar /dd-java-agent.jar

# Switch to non-root user
USER appuser

ENTRYPOINT ["java", "-javaagent:/dd-java-agent.jar", "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}", "-jar", "app.jar"]
