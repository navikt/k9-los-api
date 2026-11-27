# syntax=docker/dockerfile:1.7.0-labs
FROM ghcr.io/navikt/sif-baseimages/java-chainguard-25:2026.11.26.0840Z

LABEL org.opencontainers.image.source=https://github.com/navikt/k9-los-api

WORKDIR /app
COPY --link target/lib/ /app/lib/
COPY --link target/app.jar /app/app.jar

EXPOSE 8020