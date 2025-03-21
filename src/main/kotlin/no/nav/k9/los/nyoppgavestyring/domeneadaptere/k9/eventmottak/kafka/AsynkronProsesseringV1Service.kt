package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka

import no.nav.k9.los.Configuration
import no.nav.k9.los.fagsystem.k9sak.K9SakStream
import no.nav.k9.los.fagsystem.k9sak.K9sakEventHandlerV2
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageEventHandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageKafkaStream
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventHandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjKafkaStream
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventHandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakKafkaStream
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeKafkaStream
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventHandler
import org.slf4j.LoggerFactory

internal class AsynkronProsesseringV1Service(
    kafkaConfig: KafkaConfig,
    kafkaAivenConfig: IKafkaConfig,
    configuration: Configuration,
    k9sakEventHandler: K9SakEventHandler,
    k9KlageEventHandler: K9KlageEventHandler,
    k9sakEventHandlerv2: K9sakEventHandlerV2,
    k9TilbakeEventHandler: K9TilbakeEventHandler,
    k9PunsjEventHandler: K9PunsjEventHandler,
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(AsynkronProsesseringV1Service::class.java)
    }

    private val aksjonspunktStream = K9SakKafkaStream(
        kafkaConfig = if (configuration.k9SakConsumerAiven()) kafkaAivenConfig else kafkaConfig,
        configuration = configuration,
        k9sakEventHandler = k9sakEventHandler
    )

    //TODO: V2 - skal fjernes
    private val k9SakStream = K9SakStream(
        kafkaConfig = kafkaAivenConfig,
        configuration = configuration,
        k9sakEventHandlerv2 = k9sakEventHandlerv2
    )

    private val k9KlageStream = K9KlageKafkaStream(
        kafkaConfig = kafkaAivenConfig,
        configuration = configuration,
        k9KlageEventHandler = k9KlageEventHandler,
    )

    private val aksjonspunkTilbakeStream = K9TilbakeKafkaStream(
        kafkaConfig = if (configuration.tilbakeConsumerAiven()) kafkaAivenConfig else kafkaConfig,
        configuration = configuration,
        k9TilbakeEventHandler = k9TilbakeEventHandler
    )

    private val aksjonspunkPunsjStream = K9PunsjKafkaStream(
        kafkaConfig = if (configuration.punsjConsumerAiven()) kafkaAivenConfig else kafkaConfig,
        configuration = configuration,
        k9PunsjEventHandler = k9PunsjEventHandler
    )

    private val healthChecks = setOf(
        k9SakStream.healthy,
        aksjonspunktStream.healthy,
        k9KlageStream.healthy,
        aksjonspunkTilbakeStream.healthy,
        aksjonspunkPunsjStream.healthy
    )

    private val isReadyChecks = setOf(
        k9SakStream.ready,
        aksjonspunktStream.ready,
        k9KlageStream.ready,
        aksjonspunkTilbakeStream.ready,
        aksjonspunkPunsjStream.ready
    )

    internal fun stop() {
        logger.info("Stopper streams.")
        aksjonspunktStream.stop()
        k9SakStream.stop()
        aksjonspunkTilbakeStream.stop()
        aksjonspunkPunsjStream.stop()
        logger.info("Alle streams stoppet.")
    }

    internal fun isReadyChecks() = isReadyChecks
    internal fun isHealtyChecks() = healthChecks
}
