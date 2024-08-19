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

internal class AksjonspunktTilbakeStream constructor(
    kafkaConfig: IKafkaConfig,
    configuration: Configuration,
    k9TilbakeEventHandler: K9TilbakeEventHandler
) {

    private val stream = ManagedKafkaStreams(
        name = NAME,
        properties = kafkaConfig.stream(NAME, OffsetResetStrategy.EARLIEST),
        topology = topology(
            configuration = configuration,
            k9TilbakeEventHandler = k9TilbakeEventHandler
        ),
        unreadyAfterStreamStoppedIn = kafkaConfig.unreadyAfterStreamStoppedIn
    )

    internal val ready = ManagedStreamReady(stream)
    internal val healthy = ManagedStreamHealthy(stream)

    private companion object {
        private const val NAME = "TilbakeV1"
        private val log = LoggerFactory.getLogger("no.nav.$NAME.topology")

        private fun topology(
            configuration: Configuration,
            k9TilbakeEventHandler: K9TilbakeEventHandler
        ): Topology {
            val builder = StreamsBuilder()
            val fromTopic = Topic(
                name = configuration.getAksjonspunkthendelseTilbakeTopic(),
                serDes = AksjonspunktLagetTilbake()
            )
            builder
                .stream(
                    fromTopic.name,
                    Consumed.with(fromTopic.keySerde, fromTopic.valueSerde)
                )
                .foreach { _, entry ->
                    if (entry != null) {
                        OpentelemetrySpanUtil.span("NAME", mapOf("saksnummer" to entry.saksnummer)) {
                            log.info("Prosesserer entry fra tilbakekreving ${entry.tryggPrint()}")
                            TransientFeilHåndterer().utfør(NAME) { k9TilbakeEventHandler.prosesser(entry) }
                        }
                    }
                }
            return builder.build()
        }
    }

    internal fun stop() = stream.stop(becauseOfError = false)
}
