package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj

import no.nav.k9.los.Configuration
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.ManagedKafkaStreams
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.ManagedStreamHealthy
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.ManagedStreamReady
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.AksjonspunktPunsjLaget
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.Topic
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.IKafkaConfig
import no.nav.k9.los.utils.OpentelemetrySpanUtil
import no.nav.k9.los.utils.TransientFeilHåndterer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class K9PunsjKafkaStream constructor(
    kafkaConfig: IKafkaConfig,
    configuration: Configuration,
    k9PunsjEventHandler: K9PunsjEventHandler
) {

    private val stream = ManagedKafkaStreams(
        name = NAME,
        properties = kafkaConfig.stream(NAME, OffsetResetStrategy.EARLIEST),
        topology = topology(
            configuration = configuration,
            K9punsjEventHandler = k9PunsjEventHandler
        ),
        unreadyAfterStreamStoppedIn = kafkaConfig.unreadyAfterStreamStoppedIn
    )

    internal val ready = ManagedStreamReady(stream)
    internal val healthy = ManagedStreamHealthy(stream)

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(K9PunsjKafkaStream::class.java)
        private const val NAME = "AksjonspunktLagetPunsjV1"

        private fun topology(
            configuration: Configuration,
            K9punsjEventHandler: K9PunsjEventHandler
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
                        OpentelemetrySpanUtil.span(NAME, mapOf("journalpostId" to entry.journalpostId.verdi)) {
                            val spørring = System.currentTimeMillis()
                            logger.info("--> Mottatt hendelse fra punsj: ${entry.eksternId} - ${entry.journalpostId}")
                            TransientFeilHåndterer().utfør(NAME) { K9punsjEventHandler.prosesser(entry) }
                            logger.info("Ferdig prosessert hendelse fra punsj etter ${System.currentTimeMillis() - spørring}ms: ${entry.eksternId} - ${entry.journalpostId}.")
                        }
                    }
                }
            return builder.build()
        }
    }

    internal fun stop() = stream.stop(becauseOfError = false)
}
