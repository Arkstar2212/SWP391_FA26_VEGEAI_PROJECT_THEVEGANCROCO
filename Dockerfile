# ============================================================
# Build the Spring Boot application inside Docker
# ============================================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

# Keep dependency resolution cacheable when only source files change.
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

# ============================================================
# Minimal Production JRE Runtime
# ============================================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for container health check & configure Asia/Ho_Chi_Minh timezone
RUN apk add --no-cache curl tzdata && \
    cp /usr/share/zoneinfo/Asia/Ho_Chi_Minh /etc/localtime && \
    echo "Asia/Ho_Chi_Minh" > /etc/timezone

# Run as non-root user for enterprise container security
RUN addgroup -S vegeai && adduser -S vegeai -G vegeai
RUN mkdir -p /app/uploads/content-interactions /app/uploads/community && chown -R vegeai:vegeai /app/uploads
USER vegeai:vegeai

# Copy the JAR produced by the build stage
COPY --from=build /workspace/target/*.jar app.jar

# Expose backend port
EXPOSE 8081

# JVM memory flags and execution
ENTRYPOINT ["java", \
    "-XX:+UseG1GC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
