FROM ghcr.io/navikt/sif-baseimages/java-21:2025.06.04.0911Z

LABEL org.opencontainers.image.source=https://github.com/navikt/k9-los-api

COPY build/resources/main/init-scripts/run.sh /init-scripts/
COPY build/libs/app.jar /app/app.jar
