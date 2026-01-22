# syntax=docker/dockerfile:1.7.0-labs
FROM ghcr.io/navikt/sif-baseimages/java-21:2025.12.03.1527Z

LABEL org.opencontainers.image.source=https://github.com/navikt/k9-los-api

COPY --link target/lib/ /app/lib/
COPY --link target/app.jar /app/app.jar

EXPOSE 8020