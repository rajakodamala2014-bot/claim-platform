# syntax=docker/dockerfile:1.7
# One Dockerfile for every Spring Boot module. Usage (compose does this):
#   docker build -f deployment/docker/service.Dockerfile --build-arg SERVICE=claims-service -t claims-service .
FROM maven:3.9.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -q -pl ${SERVICE} -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG SERVICE
RUN groupadd --system app && useradd --system --gid app --home /app app \
    && mkdir -p /app /data/documents && chown -R app:app /app /data
WORKDIR /app
COPY --from=build --chown=app:app /src/${SERVICE}/target/${SERVICE}.jar /app/app.jar
USER app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
