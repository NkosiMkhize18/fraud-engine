# syntax=docker/dockerfile:1

# ---- Build stage ----
# Tests are skipped here; CI runs `./mvnw verify` before building the image.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests && cp target/*.jar app.jar

# ---- Runtime stage: JRE only, non-root ----
FROM eclipse-temurin:25-jre
# Fixed numeric UID/GID: runs as an unprivileged user with a stable id.
RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --no-create-home --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir -p /app && chown -R app:app /app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar app.jar

USER 10001:10001
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8090
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
