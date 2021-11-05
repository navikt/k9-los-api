package no.nav.k9.aksjonspunktbehandling

import no.nav.k9.Configuration
import no.nav.k9.integrasjon.kafka.KafkaConfig
import no.nav.k9.integrasjon.kafka.ManagedKafkaStreams
import no.nav.k9.integrasjon.kafka.ManagedStreamHealthy
import no.nav.k9.integrasjon.kafka.ManagedStreamReady
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed

internal class AksjonspunktPunsjStream constructor(
    kafkaConfig: KafkaConfig,
    configuration: Configuration,
    K9punsjEventHandler: K9punsjEventHandler
) {

    private val stream = ManagedKafkaStreams(
        name = NAME,
        properties = kafkaConfig.stream(NAME),
        topology = topology(
            configuration = configuration,
            K9punsjEventHandler = K9punsjEventHandler
        ),
        unreadyAfterStreamStoppedIn = kafkaConfig.unreadyAfterStreamStoppedIn
    )

    internal val ready = ManagedStreamReady(stream)
    internal val healthy = ManagedStreamHealthy(stream)

    private companion object {
        private const val NAME = "AksjonspunktLagetPunsjV1"

        private fun topology(
            configuration: Configuration,
            K9punsjEventHandler: K9punsjEventHandler
        ): Topology {
            val builder = StreamsBuilder()
            val fromTopic = Topic(
                name = configuration.getAksjonspunkthendelsePunsjTopic(),
                serDes = AksjonspunktPunsjLaget()
            )
            builder
                .stream(
                    fromTopic.name,
                    Consumed.with(fromTopic.keySerde, fromTopic.valueSerde)
                )
                .foreach { _, entry ->
                    if (entry != null) {
                        K9punsjEventHandler.prosesser(entry)
                    }
                }
            return builder.build()
        }
    }

    internal fun stop() = stream.stop(becauseOfError = false)
}
