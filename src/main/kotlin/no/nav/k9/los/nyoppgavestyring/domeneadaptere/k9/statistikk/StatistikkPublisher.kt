package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk

import no.nav.helse.dusseldorf.ktor.health.HealthCheck
import no.nav.helse.dusseldorf.ktor.health.Healthy
import no.nav.helse.dusseldorf.ktor.health.Result
import no.nav.helse.dusseldorf.ktor.health.UnHealthy
import no.nav.k9.los.Configuration
import no.nav.k9.los.integrasjon.kafka.IKafkaConfig
import no.nav.k9.los.integrasjon.kafka.TopicEntry
import no.nav.k9.los.integrasjon.kafka.TopicUse
import no.nav.k9.los.utils.LosObjectMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.json.JSONObject
import org.slf4j.LoggerFactory

class StatistikkPublisher(
    val kafkaConfig: IKafkaConfig,
    val config: Configuration
) : HealthCheck {

    private val TOPIC_USE_STATISTIKK_SAK = TopicUse(
        name = config.getÅpenStatistikkSakTopic(),
        valueSerializer = Serializer()
    )

    private val TOPIC_USE_STATISTIKK_BEHANDLING = TopicUse(
        name = config.getÅpenStatistikkBehandlingTopic(),
        valueSerializer = Serializer()
    )

    private companion object {
        private const val NAME = "NyStatistikkPublisher"

        private val log = LoggerFactory.getLogger(StatistikkPublisher::class.java)
    }

    private val producer: KafkaProducer<String, String> = KafkaProducer(
        kafkaConfig.producer(NAME),
        StringSerializer(),
        StringSerializer()
    )

    fun publiser(sak: Sak, behandling: Behandling) {
        /*
        if (config.koinProfile() == KoinProfile.LOCAL) {
            return
        }
        */
        send(sak, sak.saksnummer, TOPIC_USE_STATISTIKK_SAK.name)
        send(behandling, behandling.behandlingId, TOPIC_USE_STATISTIKK_BEHANDLING.name)
    }

    private fun send(melding: Any, key: String, topic: String) {
        /*if (config.koinProfile() == KoinProfile.LOCAL) {
            log.info("Lokal kjøring, sender ikke melding til statistikk")
            return
        }
*/
        val publiserStatistikk = System.currentTimeMillis()
        val meldingJson = LosObjectMapper.instance.writeValueAsString(melding)
        producer.send(ProducerRecord(topic, key, meldingJson)) { _, exception ->
            if (exception != null) {
                log.error("", exception)
            }
        }.get()
    }

    internal fun stop() = producer.close()

    override suspend fun check(): Result {
        val result = try {
            producer.partitionsFor(TOPIC_USE_STATISTIKK_SAK.name)
            Healthy(NAME, "Tilkobling til Kafka OK!")
        } catch (cause: Throwable) {
            log.error("Feil ved tilkobling til Kafka", cause)
            UnHealthy(NAME, "Feil ved tilkobling mot Kafka. ${cause.message}")
        }

        try {
            producer.partitionsFor(TOPIC_USE_STATISTIKK_BEHANDLING.name)
            Healthy(NAME, "Tilkobling til Kafka OK!")
        } catch (cause: Throwable) {
            log.error("Feil ved tilkobling til Kafka", cause)
            return UnHealthy(NAME, "Feil ved tilkobling mot Kafka. ${cause.message}")
        }
        return result
    }
}

private class Serializer : org.apache.kafka.common.serialization.Serializer<TopicEntry<JSONObject>> {
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