package no.nav.k9.los.domeneadaptere.statistikk

import no.nav.helse.dusseldorf.ktor.health.HealthCheck
import no.nav.helse.dusseldorf.ktor.health.Healthy
import no.nav.helse.dusseldorf.ktor.health.Result
import no.nav.helse.dusseldorf.ktor.health.UnHealthy
import no.nav.k9.los.domeneadaptere.eventmottak.kafka.IKafkaConfig
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

class StatistikkKafkaPublisher(
    kafkaConfig: IKafkaConfig,
    private val navn: String,
    private val topic: String,
) : StatistikkPublisher, HealthCheck {

    private companion object {
        private val log = LoggerFactory.getLogger(StatistikkKafkaPublisher::class.java)
    }

    private val producer: KafkaProducer<String, String> = KafkaProducer(
        kafkaConfig.producer(navn),
        StringSerializer(),
        StringSerializer()
    )

    private val asyncError = AtomicReference<Exception>(null)

    override fun publiserAsynkront(nøkkel: String, melding: Any) {
        val meldingJson = LosObjectMapper.instance.writeValueAsString(melding)
        producer.send(ProducerRecord(topic, nøkkel, meldingJson)) { _, exception ->
            if (exception != null) {
                log.error("Feil ved asynkron sending til Kafka topic={} key={}", topic, nøkkel, exception)
                asyncError.compareAndSet(null, exception)
            }
        }
    }

    override fun flushOgValider() {
        producer.flush()
        val feil = asyncError.getAndSet(null)
        if (feil != null) {
            throw feil
        }
    }

    internal fun stop() = producer.close()

    override suspend fun check(): Result {
        return try {
            producer.partitionsFor(topic)
            Healthy(navn, "Tilkobling til Kafka OK!")
        } catch (cause: Throwable) {
            log.error("Feil ved tilkobling til Kafka", cause)
            UnHealthy(navn, "Feil ved tilkobling mot Kafka. ${cause.message}")
        }
    }
}
