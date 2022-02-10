package no.nav.k9.fagsystem.k9sak

import no.nav.k9.Configuration
import no.nav.k9.aksjonspunktbehandling.AksjonspunktLaget
import no.nav.k9.aksjonspunktbehandling.K9sakEventHandler
import no.nav.k9.aksjonspunktbehandling.Topic
import no.nav.k9.integrasjon.kafka.KafkaConfig
import no.nav.k9.integrasjon.kafka.ManagedKafkaStreams
import no.nav.k9.integrasjon.kafka.ManagedStreamHealthy
import no.nav.k9.integrasjon.kafka.ManagedStreamReady
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.slf4j.LoggerFactory

internal class K9DokumentStream constructor(
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
        private const val NAME = "KravdokumentMottattHendelseV1"

        private val log = LoggerFactory.getLogger(K9DokumentStream::class.java)

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
                ).peek { _, e -> log.info("--> Kravdokumenthendelse fra k9sak: ${e.tryggToString() }") }
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
