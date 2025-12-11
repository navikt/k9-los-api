package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage

import no.nav.k9.los.Configuration
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.ManagedKafkaStreams
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.ManagedStreamHealthy
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.ManagedStreamReady
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.AksjonspunktKlageLaget
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.Topic
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.IKafkaConfig
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

internal class K9KlageKafkaStream constructor(
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

        private val log = LoggerFactory.getLogger(K9KlageKafkaStream::class.java)

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
                    Consumed.with(fromTopic.keySerde, Serdes.String())
                ).foreach { _, event ->
                    if (event != null) {
                        val tree = LosObjectMapper.instance.readTree(event)
                        val eksternId = tree.get("eksternId").asText()
                        val eksternVersjon = tree.get("eventTid").asText()
                        val saksnummer = tree.get("saksnummer").asText()

                        OpentelemetrySpanUtil.span(NAME, mapOf("saksnummer" to saksnummer)) {
                            TransientFeilHåndterer().utfør(NAME) { k9KlageEventHandler.prosesser(eksternId, eksternVersjon, event) }
                        }
                    }
                }
            return builder.build()
        }
    }

    internal fun stop() = stream.stop(becauseOfError = false)
}

fun K9KlageEventDto.tryggToString(): String {
    return """K9KlageEventDto(
            eksternId=$eksternId, 
            påklagdBehandlingId=$påklagdBehandlingId,
            påklagdBehandlingType=$påklagdBehandlingType,
            fagsystem=$fagsystem, 
            utenlandstilsnitt=$utenlandstilsnitt,
            behandlingstidFrist=$behandlingstidFrist,
            saksnummer='$saksnummer', 
            eventTid=$eventTid, 
            eventHendelse=$eventHendelse, 
            behandlingStatus=$behandlingStatus, 
            behandlingSteg=$behandlingSteg, 
            behandlendeEnhet=$behandlendeEnhet,
            ansvarligBeslutter=$ansvarligBeslutter,
            ansvarligSaksbehandler=$ansvarligSaksbehandler, 
            resultatType=$resultatType,
            ytelseTypeKode='$ytelseTypeKode', 
            behandlingTypeKode='$behandlingTypeKode', 
            opprettetBehandling=$opprettetBehandling,
            fagsakPeriode=$fagsakPeriode,
            aksjonspunktTilstander=$aksjonspunkttilstander,
            vedtaksdato=$vedtaksdato,
            behandlingsårsaker=$behandlingsårsaker
            )"""
        .trimMargin()
}
