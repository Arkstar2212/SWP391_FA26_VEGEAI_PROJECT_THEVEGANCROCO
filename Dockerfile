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
USER vegeai:vegeai

# Copy compiled JAR
COPY target/*.jar app.jar

# Expose backend port
EXPOSE 8081

# JVM memory flags and execution
ENTRYPOINT ["java", \
    "-XX:+UseG1GC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
