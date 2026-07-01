# syntax=docker/dockerfile:1

# --- build stage: compile and package the boot jar ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy build config first so dependency resolution is cached across source changes.
COPY gradlew settings.gradle build.gradle lombok.config ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Then the sources and package (tests run in CI, not in the image build).
COPY src ./src
RUN ./gradlew --no-daemon -x test bootJar

# --- runtime stage: JRE only, non-root ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Unprivileged user; the app needs no root at runtime.
RUN useradd --system --uid 1001 appuser
COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar
USER appuser

EXPOSE 8080

# Profile and connection settings come from the environment (12-factor);
# `exec` so the JVM is PID 1 and receives SIGTERM for graceful shutdown.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
