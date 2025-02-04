FROM eclipse-temurin:21-jdk-alpine as builder
WORKDIR /builder

VOLUME /tmp
COPY build/libs/app.jar /tmp/input-app.jar
#utled hvilke moduler fra jdk som trengs
RUN jdeps --print-module-deps --multi-release 21 -q --ignore-missing-deps  --add-modules ALL-MODULE-PATH /tmp/input-app.jar > modules.deps
#bygg egen java runtime med bare de modulene som trengs
RUN jlink --add-modules $(cat modules.deps) --strip-debug --no-man-pages --no-header-files --compress=1 --output javaruntime


FROM alpine:3.21.2
RUN apk update && \
    apk upgrade && \
    apk add dumb-init

LABEL org.opencontainers.image.source=https://github.com/navikt/k9-los-api

# sett opp non-root user
RUN umask o+r && \
    addgroup -S -g 1069 apprunner && \
    adduser -S -u 1069 --ingroup apprunner --no-create-home apprunner

#init-scripts, konfigurasjon
COPY build/resources/main/entrypoint.sh /
COPY build/resources/main/init-scripts/ /init-scripts/.

ENV JAVA_HOME=/opt/javaruntime
ENV PATH=$JAVA_HOME/bin:$PATH

EXPOSE 8080
WORKDIR /app
USER apprunner
ENTRYPOINT ["dumb-init", "--", "/entrypoint.sh"]

#kopier inn minimal javaruntime. Kan vurdere å lage en større som går i baseimage
COPY --from=builder /builder/javaruntime /opt/javaruntime

#spesifikt for applikasjonen (kan ikke være i baseimage)
ENV DEFAULT_JVM_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -Duser.timezone=Europe/Oslo "
COPY build/libs/app.jar /app/app.jar
