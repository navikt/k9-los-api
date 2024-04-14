package no.nav.k9.los.aksjonspunktbehandling

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.k9.klage.kontrakt.behandling.oppgavetillos.KlagebehandlingProsessHendelse
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventTilbakeDto
import no.nav.k9.los.integrasjon.kafka.dto.PunsjEventDto
import no.nav.k9.los.utils.LosObjectMapper
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

internal abstract class SerDes<V> : Serializer<V>, Deserializer<V> {

    override fun serialize(topic: String?, data: V): ByteArray? {
        return data?.let {
            LosObjectMapper.instance.writeValueAsBytes(it)
        }
    }

    override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) {}
    override fun close() {}
}

internal class AksjonspunktLaget : SerDes<BehandlingProsessEventDto>() {
    override fun deserialize(topic: String?, data: ByteArray?): BehandlingProsessEventDto? {
        return data?.let {
            return try {
                LosObjectMapper.instance.readValue(it)
            } catch (e: Exception) {
                log.warn("", e)
                log.warn(String(it))
                throw e
            }
        }
    }
}

internal class AksjonspunktKlageLaget : SerDes<KlagebehandlingProsessHendelse>() {
    override fun deserialize(topic: String?, data: ByteArray?): KlagebehandlingProsessHendelse? {
        return data?.let {
            return try {
                LosObjectMapper.instance.readValue(it)
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
                LosObjectMapper.instance.readValue(it)
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
                LosObjectMapper.instance.readValue<BehandlingProsessEventTilbakeDto>(
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
