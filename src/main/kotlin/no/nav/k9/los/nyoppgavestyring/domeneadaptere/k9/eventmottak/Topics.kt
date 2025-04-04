package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.k9.klage.kontrakt.behandling.oppgavetillos.KlagebehandlingProsessHendelse
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjEventDto
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

internal class AksjonspunktLaget : SerDes<K9SakEventDto>() {
    override fun deserialize(topic: String?, data: ByteArray?): K9SakEventDto? {
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

internal class AksjonspunktKlageLaget : SerDes<K9KlageEventDto>() {
    override fun deserialize(topic: String?, data: ByteArray?): K9KlageEventDto? {
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

internal class AksjonspunktLagetTilbake : SerDes<K9TilbakeEventDto>() {
    override fun deserialize(topic: String?, data: ByteArray?): K9TilbakeEventDto? {
        return data?.let {
            return try {
                LosObjectMapper.instance.readValue<K9TilbakeEventDto>(
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
