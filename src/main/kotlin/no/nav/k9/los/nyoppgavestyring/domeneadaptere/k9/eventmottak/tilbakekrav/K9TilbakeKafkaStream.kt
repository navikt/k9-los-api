package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav

import no.nav.k9.los.Configuration
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.IKafkaConfig
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.ManagedKafkaStreams
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.ManagedStreamHealthy
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.ManagedStreamReady
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.AksjonspunktLagetTilbake
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.Topic
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakKafkaStream
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.OpentelemetrySpanUtil
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.TransientFeilHåndterer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.slf4j.LoggerFactory

internal class K9TilbakeKafkaStream constructor(
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
                    Consumed.with(fromTopic.keySerde, Serdes.String())
                 )
                .foreach { _, event ->
                    if (event != null) {
                        val tree = LosObjectMapper.instance.readTree(event)
                        val eksternId = tree.findValue("eksternId").asText()
                        val eksternVersjon = tree.findValue("eventTid").asText()
                        val saksnummer = tree.findValue("saksnummer").asText()

                        OpentelemetrySpanUtil.span(NAME, mapOf("saksnummer" to saksnummer)) {
                            log.info("Mottar Behandlingsprosesshendelse fra k9tilbake for ${saksnummer}-${eksternId}")
                            TransientFeilHåndterer().utfør(NAME) {
                                k9TilbakeEventHandler.prosesser(eksternId, eksternVersjon, event)
                            }
                        }
                    }
                }
            return builder.build()
        }
    }

    internal fun stop() = stream.stop(becauseOfError = false)
}
