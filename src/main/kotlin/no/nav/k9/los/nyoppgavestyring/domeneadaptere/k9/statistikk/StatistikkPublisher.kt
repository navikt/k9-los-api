package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk

import no.nav.k9.los.ktor.helsesjekk.Healthy
import no.nav.k9.los.ktor.helsesjekk.HealthCheck
import no.nav.k9.los.ktor.helsesjekk.Result
import no.nav.k9.los.ktor.helsesjekk.UnHealthy
import no.nav.k9.los.Configuration
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.IKafkaConfig
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory

class StatistikkPublisher(
    val kafkaConfig: IKafkaConfig,
    val config: Configuration
) : HealthCheck {

    private val statistikkSakTopic = config.getÅpenStatistikkSakTopic()
    private val statistikkBehandlingTopic = config.getÅpenStatistikkBehandlingTopic()

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
        send(sak, sak.saksnummer, statistikkSakTopic)
        send(behandling, behandling.behandlingId, statistikkBehandlingTopic)
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
            producer.partitionsFor(statistikkSakTopic)
            Healthy(NAME, "Tilkobling til Kafka OK!")
        } catch (cause: Throwable) {
            log.error("Feil ved tilkobling til Kafka", cause)
            UnHealthy(NAME, "Feil ved tilkobling mot Kafka. ${cause.message}")
        }

        try {
            producer.partitionsFor(statistikkBehandlingTopic)
            Healthy(NAME, "Tilkobling til Kafka OK!")
        } catch (cause: Throwable) {
            log.error("Feil ved tilkobling til Kafka", cause)
            return UnHealthy(NAME, "Feil ved tilkobling mot Kafka. ${cause.message}")
        }
        return result
    }
}