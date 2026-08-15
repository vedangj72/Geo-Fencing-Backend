# Multi-stage Dockerfile for Ktor Backend
# 1. Build Stage using official Gradle JDK 17 image
FROM gradle:8.7-jdk17-alpine AS build
WORKDIR /app

# Copy project source and configuration
COPY --chown=gradle:gradle . .

# Build application distribution
RUN gradle installDist --no-daemon -x test

# 2. Production Runtime Stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy built distribution from build stage
COPY --from=build /app/build/install/Geo-Fencing-Backend /app

# Expose port (Render automatically assigns PORT env variable)
ENV PORT=8080
EXPOSE 8080

# Run Application
CMD ["/app/bin/Geo-Fencing-Backend"]
