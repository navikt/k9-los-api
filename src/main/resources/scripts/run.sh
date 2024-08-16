#!/usr/bin/env sh
set -eu
echo "Setting SYSTEMBRUKER_USERNAME and SYSTEMBRUKER_PASSWORD"
if test -f /var/run/secrets/nais.io/serviceuser/username
then
    export SYSTEMBRUKER_USERNAME="$(cat /var/run/secrets/nais.io/serviceuser/username)"
    export SYSTEMBRUKER_PASSWORD="$(cat /var/run/secrets/nais.io/serviceuser/password)"
fi


echo "Importing Azure credentials"

if test -d /var/run/secrets/nais.io/azuread;
then
    for FILE in /var/run/secrets/nais.io/azuread/*
    do
        FILE_NAME=$(echo $FILE | sed 's:.*/::')
        KEY=AZURE_$FILE_NAME
        VALUE=$(cat "$FILE")

        echo "- exporting $KEY"
        export "$KEY"="$VALUE"
    done
fi

#start opentelemetyr-agenten dersom konfigurert
set +u
if [ ! -z "${OTEL_EXPORTER_OTLP_ENDPOINT}" ]; then
    echo "Configuring attatchment of opentelemetry-agent"
    JAVA_OPTS="${JAVA_OPTS} -javaagent:/app/opentelemetry-javaagent.jar"
fi
