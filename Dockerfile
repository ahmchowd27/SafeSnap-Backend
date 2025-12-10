# Multi-stage Dockerfile: build the spring boot jar then run on a slim JRE

FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace
# Copy everything and use the included Gradle wrapper
COPY . /workspace
# Ensure wrapper is executable
RUN chmod +x ./gradlew || true
# Build jar (skip tests for faster builds in CI)
RUN ./gradlew bootJar -x test --no-daemon

# Runtime image
FROM eclipse-temurin:21-jre
WORKDIR /app
ARG JAR_FILE=build/libs/safesnap-backend.jar
COPY --from=builder /workspace/${JAR_FILE} /app/app.jar
# Constrain JVM memory for Cloud Run / small instances
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxRAMPercentage=75.0"
ENV PORT=8080
EXPOSE 8080
# Cloud Run expects the process to listen on $PORT
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -Dserver.port=${PORT} -jar /app/app.jar"]
