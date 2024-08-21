FROM ghcr.io/navikt/baseimages/temurin:21-appdynamics

LABEL org.opencontainers.image.source=https://github.com/navikt/k9-los-api

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -Duser.timezone=Europe/Oslo "

RUN curl -L -O https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.7.0/opentelemetry-javaagent.jar

COPY build/libs/app.jar ./
COPY build/resources/main/scripts /init-scripts/.

