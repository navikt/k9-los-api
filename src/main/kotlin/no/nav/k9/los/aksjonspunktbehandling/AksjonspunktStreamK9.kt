package no.nav.k9.los.aksjonspunktbehandling

import no.nav.k9.los.Configuration
import no.nav.k9.los.integrasjon.kafka.IKafkaConfig
import no.nav.k9.los.integrasjon.kafka.ManagedKafkaStreams
import no.nav.k9.los.integrasjon.kafka.ManagedStreamHealthy
import no.nav.k9.los.integrasjon.kafka.ManagedStreamReady
import no.nav.k9.los.utils.OpentelemetrySpanUtil
import no.nav.k9.los.utils.TransientFeilHåndterer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.slf4j.LoggerFactory
import kotlin.system.measureTimeMillis
import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal class AksjonspunktStreamK9 constructor(
    kafkaConfig: IKafkaConfig,
    configuration: Configuration,
    k9sakEventHandler: K9sakEventHandler
) {

    private val stream = ManagedKafkaStreams(
        name = NAME,
        properties = kafkaConfig.stream(NAME, OffsetResetStrategy.EARLIEST),
        topology = topology(
            configuration = configuration,
            k9sakEventHandler = k9sakEventHandler
        ),
        unreadyAfterStreamStoppedIn = kafkaConfig.unreadyAfterStreamStoppedIn
    )

    internal val ready = ManagedStreamReady(stream)
    internal val healthy = ManagedStreamHealthy(stream)

    private companion object {
        private const val NAME = "AksjonspunktLagetV1"

        private val log = LoggerFactory.getLogger(AksjonspunktStreamK9::class.java)

        private fun topology(
            configuration: Configuration,
            k9sakEventHandler: K9sakEventHandler
        ): Topology {
            val builder = StreamsBuilder()
            val fromTopic = Topic(
                name = configuration.getAksjonspunkthendelseTopic(),
                serDes = AksjonspunktLaget()
            )
            builder
                .stream(
                    fromTopic.name,
                    Consumed.with(fromTopic.keySerde, fromTopic.valueSerde)
                ).peek { _, e -> log.info("--> Behandlingsprosesshendelse fra k9sak: ${e.tryggToString() }") }
                .foreach { _, entry ->
                    if (entry != null) {
                        OpentelemetrySpanUtil.span("AksjonspunktStreamK9") {
                            val tid = measureTimeMillis {
                                TransientFeilHåndterer(warningEtter = 5.toDuration(DurationUnit.SECONDS)).utfør(NAME) {k9sakEventHandler.prosesser(entry) }
                            }
                            if (tid > 5000) {
                                // Logger som warning ved over 5sekunder fordi det kan oppleves som at oppgaver blir liggende igjen på benken
                                log.warn("Prosessering av Behandlingsprosesshendelse fra k9sak for ${entry.saksnummer}-${entry.eksternId} tok $tid")
                            } else {
                                log.info("Prosessering av Behandlingsprosesshendelse fra k9sak for ${entry.saksnummer}-${entry.eksternId} tok $tid")
                            }
                        }
                    }
                }
            return builder.build()
        }
    }

    internal fun stop() = stream.stop(becauseOfError = false)
}
