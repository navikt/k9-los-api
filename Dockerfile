# syntax=docker/dockerfile:1.7.0-labs
FROM ghcr.io/navikt/sif-baseimages/java-25:2026.05.07.0728Z

LABEL org.opencontainers.image.source=https://github.com/navikt/k9-los-api

COPY --link target/lib/ /app/lib/
COPY --link target/app.jar /app/app.jar

EXPOSE 8020