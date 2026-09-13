FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle settings.gradle
COPY build.gradle build.gradle
COPY src src

RUN chmod +x ./gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre

WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar /app/app.jar

# Provide Docker/Podman CLIs for socket-based local Job execution from inside the container.
RUN apt-get update \
	&& apt-get install -y --no-install-recommends docker.io podman \
	&& rm -rf /var/lib/apt/lists/*

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

