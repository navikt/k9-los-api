# syntax=docker/dockerfile:1.7.0-labs
FROM ghcr.io/navikt/sif-baseimages/java-25:2026.02.06.0908Z

LABEL org.opencontainers.image.source=https://github.com/navikt/k9-los-api

COPY --link target/lib/ /app/lib/
COPY --link target/app.jar /app/app.jar

EXPOSE 8020