package no.nav.k9.los.domeneadaptere.statistikk.avp

import no.nav.helse.dusseldorf.ktor.health.HealthCheck
import no.nav.helse.dusseldorf.ktor.health.Healthy
import no.nav.helse.dusseldorf.ktor.health.Result
import no.nav.helse.dusseldorf.ktor.health.UnHealthy
import no.nav.k9.los.Configuration
import no.nav.k9.los.domeneadaptere.eventmottak.kafka.IKafkaConfig
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

class AvpStatistikkPublisher(
    val kafkaConfig: IKafkaConfig,
    val config: Configuration
) : HealthCheck {

    private val statistikkBehandlingTopic = config.getÅpenAvpStatistikkBehandlingTopic()

    private companion object {
        private const val NAME = "AvpStatistikkPublisher"

        private val log = LoggerFactory.getLogger(AvpStatistikkPublisher::class.java)
    }

    private val producer: KafkaProducer<String, String> = KafkaProducer(
        kafkaConfig.producer(NAME),
        StringSerializer(),
        StringSerializer()
    )

    private val asyncError = AtomicReference<Exception>(null)

    /**
     * Legger meldinger i producer-bufferen uten å blokkere.
     * Kall [flushOgValider] periodisk for å sikre levering og oppdage feil.
     */
    fun publiserAsynkront(behandling: AvpBehandling) {
        val meldingJson = LosObjectMapper.instance.writeValueAsString(behandling)
        producer.send(ProducerRecord(statistikkBehandlingTopic, behandling.behandlingId, meldingJson)) { _, exception ->
            if (exception != null) {
                log.error(
                    "Feil ved asynkron sending til Kafka topic={} key={}",
                    statistikkBehandlingTopic,
                    behandling.behandlingId,
                    exception
                )
                asyncError.compareAndSet(null, exception)
            }
        }
    }

    /**
     * Blokkerer til alle buffrede meldinger er sendt og bekreftet.
     * Kaster exception hvis noen av de asynkrone sendingene feilet.
     */
    fun flushOgValider() {
        producer.flush()
        val feil = asyncError.getAndSet(null)
        if (feil != null) {
            throw feil
        }
    }

    internal fun stop() = producer.close()

    override suspend fun check(): Result {
        return try {
            producer.partitionsFor(statistikkBehandlingTopic)
            Healthy(NAME, "Tilkobling til Kafka OK!")
        } catch (cause: Throwable) {
            log.error("Feil ved tilkobling til Kafka", cause)
            UnHealthy(NAME, "Feil ved tilkobling mot Kafka. ${cause.message}")
        }
    }
}
