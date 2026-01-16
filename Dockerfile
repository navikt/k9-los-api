FROM ghcr.io/navikt/sif-baseimages/java-21:2025.12.03.1527Z

LABEL org.opencontainers.image.source=https://github.com/navikt/k9-los-api

# Copy init scripts
COPY build/resources/main/init-scripts/run.sh /init-scripts/

# Copy dependencies first (changes less frequently, better layer caching)
COPY target/lib/ /app/lib/

# Copy application JAR last (changes more frequently)
COPY target/app.jar /app/app.jar
