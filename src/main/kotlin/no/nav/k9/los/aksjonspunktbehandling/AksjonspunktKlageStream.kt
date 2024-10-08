package no.nav.k9.los.aksjonspunktbehandling

import no.nav.k9.los.Configuration
import no.nav.k9.los.integrasjon.kafka.*
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

internal class AksjonspunktKlageStream constructor(
    kafkaConfig: IKafkaConfig,
    configuration: Configuration,
    k9KlageEventHandler: K9KlageEventHandler
) {

    private val stream = ManagedKafkaStreams(
        name = NAME,
        properties = kafkaConfig.stream(NAME, OffsetResetStrategy.EARLIEST),
        topology = topology(
            configuration = configuration,
            k9KlageEventHandler = k9KlageEventHandler
        ),
        unreadyAfterStreamStoppedIn = kafkaConfig.unreadyAfterStreamStoppedIn
    )

    internal val ready = ManagedStreamReady(stream)
    internal val healthy = ManagedStreamHealthy(stream)

    private companion object {
        private const val NAME = "AksjonspunktLagetKlageV1"

        private val log = LoggerFactory.getLogger(AksjonspunktKlageStream::class.java)

        private fun topology(
            configuration: Configuration,
            k9KlageEventHandler: K9KlageEventHandler
        ): Topology {
            val builder = StreamsBuilder()
            val fromTopic = Topic(
                name = configuration.getKlageOppgavemeldingerTopic(),
                serDes = AksjonspunktKlageLaget()
            )
            builder
                .stream(
                    fromTopic.name,
                    Consumed.with(fromTopic.keySerde, fromTopic.valueSerde)
                ).peek { _, e -> log.info("--> Behandlingsprosesshendelse fra k9klage: ${e.tryggToString() }") }
                .foreach { _, entry ->
                    if (entry != null) {
                        OpentelemetrySpanUtil.span(NAME, mapOf("saksnummer" to entry.saksnummer)) {
                            TransientFeilHåndterer().utfør(NAME) { k9KlageEventHandler.prosesser(entry) }
                        }
                    }
                }
            return builder.build()
        }
    }

    internal fun stop() = stream.stop(becauseOfError = false)
}
