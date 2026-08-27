package no.nav.k9.los.domeneadaptere.ung.eventmottak.ungtilbake

import no.nav.k9.los.Configuration
import no.nav.k9.los.domeneadaptere.kafka.IKafkaConfig
import no.nav.k9.los.domeneadaptere.kafka.ManagedKafkaStreams
import no.nav.k9.los.domeneadaptere.kafka.ManagedStreamHealthy
import no.nav.k9.los.domeneadaptere.kafka.ManagedStreamReady
import no.nav.k9.los.domeneadaptere.k9.eventmottak.AksjonspunktLaget
import no.nav.k9.los.domeneadaptere.k9.eventmottak.Topic
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.infrastruktur.utils.OpentelemetrySpanUtil
import no.nav.k9.los.infrastruktur.utils.TransientFeilHåndterer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.slf4j.LoggerFactory
import kotlin.system.measureTimeMillis
import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal class UngTilbakeKafkaStream constructor(
    kafkaConfig: IKafkaConfig,
    configuration: Configuration,
    ungTilbakeEventHandler: UngTilbakeEventHandler
) {

    private val stream = ManagedKafkaStreams(
        name = NAME,
        properties = kafkaConfig.stream(NAME, OffsetResetStrategy.EARLIEST).apply {
            put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, 3) // topic har 3 partisjoner
        },
        topology = topology(
            configuration = configuration,
            ungTilbakeEventHandler = ungTilbakeEventHandler
        ),
        unreadyAfterStreamStoppedIn = kafkaConfig.unreadyAfterStreamStoppedIn
    )

    internal val ready = ManagedStreamReady(stream)
    internal val healthy = ManagedStreamHealthy(stream)

    private companion object {
        private const val NAME = "UngTilbakeKafkaStream"

        private val log = LoggerFactory.getLogger(UngTilbakeKafkaStream::class.java)

        private fun topology(
            configuration: Configuration,
            ungTilbakeEventHandler: UngTilbakeEventHandler
        ): Topology {
            val builder = StreamsBuilder()
            val fromTopic = Topic(
                name = configuration.getUngTilbakeHendelseTopic(),
                serDes = AksjonspunktLaget()
            )
            builder
                .stream(
                    fromTopic.name,
                    Consumed.with(fromTopic.keySerde, Serdes.String())
                ).foreach { _, event ->
                    if (event != null) {
                        val tree = LosObjectMapper.instance.readTree(event)
                        val eksternId = tree.get("eksternId").asText()
                        val eksternVersjon = tree.get("eventTid").asText()
                        val saksnummer = tree.get("saksnummer").asText()

                        log.info("Mottar Behandlingsprosesshendelse fra ung-tilbake for ${saksnummer}-${eksternId}")

                        OpentelemetrySpanUtil.span(NAME, mapOf("saksnummer" to saksnummer)) {
                            val tid = measureTimeMillis {
                                TransientFeilHåndterer(warningEtter = 5.toDuration(DurationUnit.SECONDS)).utfør(NAME) {
                                    ungTilbakeEventHandler.prosesser(eksternId, eksternVersjon, event)
                                }
                            }
                            if (tid > 5000) {
                                // Logger som warning ved over 5sekunder fordi det kan oppleves som at oppgaver blir liggende igjen på benken
                                log.warn("Prosessering av Behandlingsprosesshendelse fra ung-tilbake for ${saksnummer}-${eksternId} tok $tid")
                            } else {
                                log.info("Prosessering av Behandlingsprosesshendelse fra ung-tilbake for ${saksnummer}-${eksternId} tok $tid")
                            }
                        }
                    }
                }
            return builder.build()
        }
    }

    internal fun stop() = stream.stop(becauseOfError = false)
}
