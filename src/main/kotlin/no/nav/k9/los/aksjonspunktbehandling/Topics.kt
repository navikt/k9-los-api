package no.nav.k9.los.aksjonspunktbehandling

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventTilbakeDto
import no.nav.k9.los.integrasjon.kafka.dto.PunsjEventDto
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(Topic::class.java)

internal data class Topic<V>(
    val name: String,
    val serDes: SerDes<V>
) {
    val keySerializer = StringSerializer()
    val keySerde = Serdes.String()!!
    val valueSerde = Serdes.serdeFrom(serDes, serDes)!!
}

fun objectMapper(): ObjectMapper {
    return jacksonObjectMapper()
        .dusseldorfConfigured()
        .configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
        .enable(SerializationFeature.INDENT_OUTPUT)
        .setPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CAMEL_CASE)
}

internal abstract class SerDes<V> : Serializer<V>, Deserializer<V> {
    protected val objectMapper = objectMapper()


    override fun serialize(topic: String?, data: V): ByteArray? {
        return data?.let {
            objectMapper.writeValueAsBytes(it)
        }
    }

    override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) {}
    override fun close() {}
}

internal class AksjonspunktLaget : SerDes<BehandlingProsessEventDto>() {
    override fun deserialize(topic: String?, data: ByteArray?): BehandlingProsessEventDto? {
        return data?.let {
            return try {
                objectMapper.readValue(it)
            } catch (e: Exception) {
                log.warn("", e)
                log.warn(String(it))
                throw e
            }
        }
    }
}

internal class AksjonspunktPunsjLaget : SerDes<PunsjEventDto>() {
    override fun deserialize(topic: String?, data: ByteArray?): PunsjEventDto? {
        return data?.let {
            return try {
                objectMapper.readValue(it)
            } catch (e: Exception) {
                log.warn("", e)
                log.warn(String(it))
                throw e
            }
        }
    }
}

internal class AksjonspunktLagetTilbake : SerDes<BehandlingProsessEventTilbakeDto>() {
    override fun deserialize(topic: String?, data: ByteArray?): BehandlingProsessEventTilbakeDto? {
        return data?.let {
            return try {
                objectMapper.readValue<BehandlingProsessEventTilbakeDto>(
                    it
                )
            } catch (e: Exception) {
                log.warn("", e)
                log.warn(String(it))
                throw e
            }
        }
    }
}
