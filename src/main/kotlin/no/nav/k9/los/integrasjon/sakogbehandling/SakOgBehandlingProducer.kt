package no.nav.k9.los.integrasjon.sakogbehandling

import no.nav.helse.dusseldorf.ktor.health.HealthCheck
import no.nav.helse.dusseldorf.ktor.health.Healthy
import no.nav.helse.dusseldorf.ktor.health.Result
import no.nav.helse.dusseldorf.ktor.health.UnHealthy
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.IKafkaConfig
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.TopicEntry
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.TopicUse
import no.nav.k9.los.integrasjon.sakogbehandling.kontrakt.BehandlingAvsluttet
import no.nav.k9.los.integrasjon.sakogbehandling.kontrakt.BehandlingOpprettet
import no.nav.k9.los.utils.LosObjectMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer
import org.json.JSONObject
import org.slf4j.LoggerFactory

class SakOgBehandlingProducer constructor(
    val kafkaConfig: IKafkaConfig,
    val config: Configuration
) : HealthCheck {

    private val TOPIC_USE_SAK_OG_BEHANDLING = TopicUse(
        name = config.getSakOgBehandlingTopic(),
        valueSerializer = SakOgBehandlingSerialier()
    )
    private companion object {
        private const val NAME = "SakOgBehandlingProducer"

        private val log = LoggerFactory.getLogger(SakOgBehandlingProducer::class.java)
    }

    private val producer: KafkaProducer<String, String> = KafkaProducer(
        kafkaConfig.producer(NAME),
        StringSerializer(),
        StringSerializer()
    )

    internal fun behandlingOpprettet(
        behandlingOpprettet: BehandlingOpprettet
    ) {
        if (KoinProfile.LOCAL == config.koinProfile()) {
            return
        }
        val melding = LosObjectMapper.instance.writeValueAsString(behandlingOpprettet)
        producer.send(
            ProducerRecord(
                TOPIC_USE_SAK_OG_BEHANDLING.name,
                melding
            )
        ) { metadata, exception ->
            if (exception != null) {
                log.error("Feil vid publisering av kafka melding til sakogbehandling", exception)
            } else {
                log.trace("Melding sendt OK til sakogbehandling med offset '${metadata.offset()}' til partition '${metadata.partition()}' topic ${metadata.topic()}")
            }
        }.get()
    }

    internal fun avsluttetBehandling(
        behandlingAvsluttet: BehandlingAvsluttet
    ) {
        if (KoinProfile.LOCAL == config.koinProfile()) {
            log.info("Lokal kjøring, sender ikke melding til sak og behandling")
            return
        }
        val melding = LosObjectMapper.instance.writeValueAsString(behandlingAvsluttet)
        producer.send(
            ProducerRecord(
                TOPIC_USE_SAK_OG_BEHANDLING.name,
                melding
            )
        ).get()
    }


    internal fun stop() = producer.close()

    override suspend fun check(): Result {
        return try {
            producer.partitionsFor(TOPIC_USE_SAK_OG_BEHANDLING.name)
            Healthy(NAME, "Tilkobling til Kafka OK!")
        } catch (cause: Throwable) {
            log.error("Feil ved tilkobling til Kafka", cause)
            UnHealthy(NAME, "Feil ved tilkobling mot Kafka. ${cause.message}")
        }
    }

}

private class SakOgBehandlingSerialier :
    Serializer<TopicEntry<JSONObject>> {
    override fun serialize(topic: String, data: TopicEntry<JSONObject>): ByteArray {
        val metadata = JSONObject()
            .put("correlation_id", data.metadata.correlationId)
            .put("request_id", data.metadata.requestId)
            .put("version", data.metadata.version)

        return JSONObject()
            .put("metadata", metadata)
            .put("data", data.data)
            .toString()
            .toByteArray()
    }

    override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) {}
    override fun close() {}
}

