package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottakoghistorikk.punsj

import no.nav.k9.los.Configuration
import no.nav.k9.los.aksjonspunktbehandling.AksjonspunktPunsjLaget
import no.nav.k9.los.aksjonspunktbehandling.PunsjEventV3SerDes
import no.nav.k9.los.aksjonspunktbehandling.Topic
import no.nav.k9.los.integrasjon.kafka.*
import no.nav.k9.los.integrasjon.kafka.ManagedKafkaStreams
import no.nav.k9.los.integrasjon.kafka.ManagedStreamHealthy
import no.nav.k9.los.integrasjon.kafka.ManagedStreamReady
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class AksjonspunktPunsjV3Stream(
    kafkaConfig: IKafkaConfig,
    configuration: Configuration,
    k9punsjEventHandlerV3: K9punsjEventHandlerV3
) {

    private val stream = ManagedKafkaStreams(
        name = NAME,
        properties = kafkaConfig.stream(NAME, OffsetResetStrategy.EARLIEST),
        topology = topology(
            configuration = configuration,
            k9punsjEventHandlerV3 = k9punsjEventHandlerV3
        ),
        unreadyAfterStreamStoppedIn = kafkaConfig.unreadyAfterStreamStoppedIn
    )

    internal val ready = ManagedStreamReady(stream)
    internal val healthy = ManagedStreamHealthy(stream)

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(AksjonspunktPunsjV3Stream::class.java)
        private const val NAME = "OppgavemeldingerPunsjV3"

        private fun topology(
            configuration: Configuration,
            k9punsjEventHandlerV3: K9punsjEventHandlerV3
        ): Topology {
            val builder = StreamsBuilder()
            val fromTopic = Topic(
                name = configuration.getAksjonspunkthendelsePunsjTopic(),
                serDes = PunsjEventV3SerDes()
            )
            builder
                .stream(
                    fromTopic.name,
                    Consumed.with(fromTopic.keySerde, fromTopic.valueSerde)
                )
                .foreach { _, entry ->
                    if (entry != null) {
                        val spørring = System.currentTimeMillis()
                        logger.debug("--> Mottatt hendelse fra punsj: ${entry.eksternId} - ${entry.journalpostId}")
                        k9punsjEventHandlerV3.prosesser(entry)
                        logger.debug("Ferdig prosessert hendelse fra punsj etter ${System.currentTimeMillis() - spørring}ms: ${entry.eksternId} - ${entry.journalpostId}.")
                    }
                }
            return builder.build()
        }
    }

    internal fun stop() = stream.stop(becauseOfError = false)
}
