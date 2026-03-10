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

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

RUN chown -R appuser:appgroup /app

COPY --from=build /app/target/*.jar app.jar
RUN chown appuser:appgroup /app/app.jar

# Switch to non-root user
USER appuser

ENTRYPOINT ["java", "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}", "-jar", "app.jar"]
