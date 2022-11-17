package no.nav.k9.los.aksjonspunktbehandling

import no.nav.k9.los.Configuration
import no.nav.k9.los.integrasjon.kafka.KafkaConfig
import no.nav.k9.los.integrasjon.kafka.ManagedKafkaStreams
import no.nav.k9.los.integrasjon.kafka.ManagedStreamHealthy
import no.nav.k9.los.integrasjon.kafka.ManagedStreamReady
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.slf4j.LoggerFactory

internal class AksjonspunktTilbakeStream constructor(
    kafkaConfig: KafkaConfig,
    configuration: Configuration,
    k9TilbakeEventHandler: K9TilbakeEventHandler
) {

    private val stream = ManagedKafkaStreams(
        name = NAME,
        properties = kafkaConfig.stream(NAME),
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
                        log.info("Prosesserer entry fra tilbakekreving ${entry.tryggPrint()}")
                        k9TilbakeEventHandler.prosesser(entry)
                    }
                }
            return builder.build()
        }
    }

    internal fun stop() = stream.stop(becauseOfError = false)
}
