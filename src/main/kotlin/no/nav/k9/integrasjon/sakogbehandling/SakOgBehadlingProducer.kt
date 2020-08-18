package no.nav.k9.integrasjon.sakogbehandling

import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.dusseldorf.ktor.health.HealthCheck
import no.nav.helse.dusseldorf.ktor.health.Healthy
import no.nav.helse.dusseldorf.ktor.health.Result
import no.nav.helse.dusseldorf.ktor.health.UnHealthy
import no.nav.k9.Configuration
import no.nav.k9.KoinProfile
import no.nav.k9.aksjonspunktbehandling.objectMapper
import no.nav.k9.integrasjon.kafka.KafkaConfig
import no.nav.k9.integrasjon.kafka.TopicEntry
import no.nav.k9.integrasjon.kafka.TopicUse
import no.nav.k9.integrasjon.sakogbehandling.kontrakt.BehandlingAvsluttet
import no.nav.k9.integrasjon.sakogbehandling.kontrakt.BehandlingOpprettet
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer
import org.json.JSONObject
import org.slf4j.LoggerFactory

class SakOgBehadlingProducer @KtorExperimentalAPI constructor(
    val kafkaConfig: KafkaConfig,
    val config: Configuration
) : HealthCheck {
    @KtorExperimentalAPI
    private val TOPIC_USE_SAK_OG_BEHANDLING = TopicUse(
        name = config.getSakOgBehandlingTopic(),
        valueSerializer = SakOgBehandlingSerialier()
    )
    private companion object {
        private val NAME = "SakOgBehadlingProducer"
      
        private val log = LoggerFactory.getLogger(SakOgBehadlingProducer::class.java)
    }

    private val producer: KafkaProducer<String, String> = KafkaProducer(
        kafkaConfig.producer(NAME),
        StringSerializer(),
        StringSerializer()
    )

    @KtorExperimentalAPI
    internal fun behandlingOpprettet(
        behandlingOpprettet: BehandlingOpprettet
    ) {
        if (KoinProfile.LOCAL == config.koinProfile()) {
            return
        }
        val melding = objectMapper().writeValueAsString(behandlingOpprettet)
        val recordMetaData = producer.send(
           ProducerRecord(
                TOPIC_USE_SAK_OG_BEHANDLING.name,
               melding
            )
        ).get()
        log.info("Sendt til Topic '${TOPIC_USE_SAK_OG_BEHANDLING.name}' med offset '${recordMetaData.offset()}' til partition '${recordMetaData.partition()}'")
    }

    @KtorExperimentalAPI
    internal fun avsluttetBehandling(
        behandlingAvsluttet: BehandlingAvsluttet
    ) {
        if (KoinProfile.LOCAL == config.koinProfile()) {
            log.info("Lokal kjøring, sender ikke melding til sak og behandling")
            return
        }
        val melding = objectMapper().writeValueAsString(behandlingAvsluttet)
        val recordMetaData = producer.send(
            ProducerRecord(
                TOPIC_USE_SAK_OG_BEHANDLING.name,
                melding
            )
        ).get()
        log.info("Sendt til Topic '${TOPIC_USE_SAK_OG_BEHANDLING.name}' med offset '${recordMetaData.offset()}' til partition '${recordMetaData.partition()}'")
       // logger.info("AvsluttetBehandling: $melding")
    }


    internal fun stop() = producer.close()
    @KtorExperimentalAPI
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

