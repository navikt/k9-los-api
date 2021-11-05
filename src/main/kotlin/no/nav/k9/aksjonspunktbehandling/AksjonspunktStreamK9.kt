package no.nav.k9.aksjonspunktbehandling

import no.nav.k9.Configuration
import no.nav.k9.integrasjon.kafka.KafkaConfig
import no.nav.k9.integrasjon.kafka.ManagedKafkaStreams
import no.nav.k9.integrasjon.kafka.ManagedStreamHealthy
import no.nav.k9.integrasjon.kafka.ManagedStreamReady
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed

internal class AksjonspunktStreamK9 constructor(
    kafkaConfig: KafkaConfig,
    configuration: Configuration,
    k9sakEventHandler: K9sakEventHandler
) {

    private val stream = ManagedKafkaStreams(
        name = NAME,
        properties = kafkaConfig.stream(NAME),
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
                )
                .foreach { _, entry ->
                    if (entry != null) {
                        k9sakEventHandler.prosesser(entry)
                    }
                }
            return builder.build()
        }
    }

    internal fun stop() = stream.stop(becauseOfError = false)
}
