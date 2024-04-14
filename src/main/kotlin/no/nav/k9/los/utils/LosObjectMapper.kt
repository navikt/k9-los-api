package no.nav.k9.los.utils

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured

class LosObjectMapper {

    companion object {
        val instance = jacksonObjectMapper()
            .dusseldorfConfigured()
            .configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
            .enable(SerializationFeature.INDENT_OUTPUT) //TODO ønsker å skru av denne by default, og ha egen ObjectMapper der hvor dette trengs
            .setPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CAMEL_CASE)
    }
}