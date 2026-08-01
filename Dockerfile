FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build
COPY . .
# Use the project's own Gradle wrapper (pinned in gradle-wrapper.properties)
# rather than a fixed gradle:X-jdk21 base image's bundled Gradle -- the two
# can drift apart, and this project has only ever been built/tested with
# the wrapper's version locally.
RUN ./gradlew clean build -x test --no-daemon

FROM eclipse-temurin:21-jre

# wget for the docker-compose healthcheck -- not present in the base image.
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /build/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
