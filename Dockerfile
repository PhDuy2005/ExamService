# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

COPY ExamService/ExamService/ ./ExamService/ExamService/
COPY proto/ ./proto/

WORKDIR /workspace/ExamService/ExamService
RUN sed -i 's/\r$//' gradlew \
    && chmod +x gradlew \
    && ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --shell /usr/sbin/nologin spring \
    && mkdir -p /data/exam-service \
    && chown -R spring:spring /data/exam-service

COPY --from=build /workspace/ExamService/ExamService/build/libs/*.jar /app/app.jar

USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

