# Multi-stage Dockerfile for Ktor Backend
# 1. Build Stage
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Copy gradle files and source code
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY src src

# Make gradlew executable and build project
RUN chmod +x gradlew
RUN ./gradlew build --no-daemon -x test

# 2. Production Runtime Stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy built jar from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Expose port (Render automatically sets PORT env variable)
ENV PORT=8080
EXPOSE 8080

# Run Application
CMD ["java", "-jar", "app.jar"]
