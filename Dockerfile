FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /builder
VOLUME /tmp
COPY build/libs/app.jar /tmp/input-app.jar
#utled hvilke moduler fra jdk som trengs (og legg til jdk.crypto.ec siden den trengs for ssl)
RUN jdeps --print-module-deps --multi-release 21 -q --ignore-missing-deps  --add-modules ALL-MODULE-PATH /tmp/input-app.jar > modules.deps \
  && sed -i '1s/^/jdk.crypto.ec,/' modules.deps
#bygg egen java runtime med bare de modulene som trengs
RUN jlink --add-modules $(cat modules.deps) --strip-debug --no-man-pages --no-header-files --compress=1 --output javaruntime




FROM ghcr.io/navikt/sif-baseimages/java-base:2025.02.27.1645Z
LABEL org.opencontainers.image.source=https://github.com/navikt/k9-los-api

#kopier inn minimal javaruntime (alternativ er å buke sif-baseimage/java-<javaversjon>:<versjon> som baseimage og ikke bygge minimal javaruntime)
COPY --from=builder /builder/javaruntime /opt/java/openjdk

COPY build/resources/main/init-scripts/run.sh /init-scripts/
COPY build/libs/app.jar /app/app.jar
