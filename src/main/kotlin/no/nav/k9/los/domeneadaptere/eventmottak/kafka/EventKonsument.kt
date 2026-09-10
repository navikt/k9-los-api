package no.nav.k9.los.domeneadaptere.eventmottak.kafka

import no.nav.helse.dusseldorf.ktor.health.HealthCheck
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.infrastruktur.utils.OpentelemetrySpanUtil
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.slf4j.LoggerFactory
import kotlin.system.measureTimeMillis

/**
 * Leser behandlingshendelser fra en topic som ren tekst, plukker ut nøkkelfeltene som trengs for
 * sporing/logging, og lar handleren gjøre resten av jacksonmappingen når den faktisk trenger den.
 *
 * @param navn brukes som consumer group-suffiks (må ikke endres uten å ta hensyn til offsets), spannavn og trådnavn.
 */
internal class EventKonsument(
    kafkaConfig: IKafkaConfig,
    private val navn: String,
    private val kilde: String,
    topic: String,
    antallTråder: Int = 1,
    private val sporingsfelt: String = "saksnummer",
    private val prosesser: (eksternId: String, eksternVersjon: String, event: String) -> Unit,
) {
    private companion object {
        private val log = LoggerFactory.getLogger(EventKonsument::class.java)
        private const val TREG_PROSESSERING_MS = 5000L
    }

    private val consumer = ManagedKafkaConsumer(
        name = navn,
        topic = topic,
        properties = kafkaConfig.consumer(navn, OffsetResetStrategy.EARLIEST),
        antallTråder = antallTråder,
        unreadyEtterStoppetI = kafkaConfig.unreadyAfterConsumerStoppedIn,
        håndter = ::håndter,
    )

    internal val ready: HealthCheck get() = consumer.ready
    internal val healthy: HealthCheck get() = consumer.healthy

    internal fun stop() = consumer.stop()

    private fun håndter(melding: String) {
        val tree = LosObjectMapper.instance.readTree(melding)
        val eksternId = tree.get("eksternId").asText()
        val eksternVersjon = tree.get("eventTid").asText()
        val sporingsverdi = tree.get(sporingsfelt).asText()

        log.info("Mottar hendelse fra $kilde for $sporingsverdi-$eksternId")

        OpentelemetrySpanUtil.span(navn, mapOf(sporingsfelt to sporingsverdi)) {
            // Feiler prosesseringen, lar vi den boble ut: consumeren committer ikke, spoler
            // tilbake og prøver meldingen på nytt. Ingen egen retry-loop her.
            val tid = measureTimeMillis {
                prosesser(eksternId, eksternVersjon, melding)
            }
            if (tid > TREG_PROSESSERING_MS) {
                // Logger som warning fordi det kan oppleves som at oppgaver blir liggende igjen på benken
                log.warn("Prosessering av hendelse fra $kilde for $sporingsverdi-$eksternId tok $tid ms")
            } else {
                log.info("Prosessering av hendelse fra $kilde for $sporingsverdi-$eksternId tok $tid ms")
            }
        }
    }
}


