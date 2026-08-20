package no.nav.k9.los.domeneadaptere.kafka

import no.nav.k9.los.Configuration
import no.nav.k9.los.domeneadaptere.k9.eventmottak.klage.K9KlageEventHandler
import no.nav.k9.los.domeneadaptere.k9.eventmottak.klage.K9KlageKafkaStream
import no.nav.k9.los.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventHandler
import no.nav.k9.los.domeneadaptere.k9.eventmottak.punsj.K9PunsjKafkaStream
import no.nav.k9.los.domeneadaptere.k9.eventmottak.sak.K9SakEventHandler
import no.nav.k9.los.domeneadaptere.k9.eventmottak.sak.K9SakKafkaStream
import no.nav.k9.los.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventHandler
import no.nav.k9.los.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeKafkaStream
import no.nav.k9.los.domeneadaptere.ungsak.eventmottak.ungsak.UngSakEventHandler
import no.nav.k9.los.domeneadaptere.ungsak.eventmottak.ungsak.UngSakKafkaStream
import no.nav.k9.los.domeneadaptere.ungsak.eventmottak.ungtilbake.UngTilbakeEventHandler
import no.nav.k9.los.domeneadaptere.ungsak.eventmottak.ungtilbake.UngTilbakeKafkaStream
import org.slf4j.LoggerFactory

internal class AsynkronProsesseringV1Service(
    kafkaAivenConfig: IKafkaConfig,
    configuration: Configuration,
    k9sakEventHandler: K9SakEventHandler,
    k9KlageEventHandler: K9KlageEventHandler,
    k9TilbakeEventHandler: K9TilbakeEventHandler,
    k9PunsjEventHandler: K9PunsjEventHandler,
    ungSakEventHandler: UngSakEventHandler,
    ungTilbakeEventHandler: UngTilbakeEventHandler,
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(AsynkronProsesseringV1Service::class.java)
    }

    private val aksjonspunktStream = K9SakKafkaStream(
        kafkaConfig = kafkaAivenConfig,
        configuration = configuration,
        k9sakEventHandler = k9sakEventHandler
    )

    private val k9KlageStream = K9KlageKafkaStream(
        kafkaConfig = kafkaAivenConfig,
        configuration = configuration,
        k9KlageEventHandler = k9KlageEventHandler,
    )

    private val aksjonspunkTilbakeStream = K9TilbakeKafkaStream(
        kafkaConfig = kafkaAivenConfig,
        configuration = configuration,
        k9TilbakeEventHandler = k9TilbakeEventHandler
    )

    private val aksjonspunkPunsjStream = K9PunsjKafkaStream(
        kafkaConfig = kafkaAivenConfig,
        configuration = configuration,
        k9PunsjEventHandler = k9PunsjEventHandler
    )

    private val ungSakStream = UngSakKafkaStream(
        kafkaConfig = kafkaAivenConfig,
        configuration = configuration,
        ungSakEventHandler = ungSakEventHandler
    )

    private val ungTilbakeStream = UngTilbakeKafkaStream(
        kafkaConfig = kafkaAivenConfig,
        configuration = configuration,
        ungTilbakeEventHandler = ungTilbakeEventHandler
    )

    private val healthChecks = setOf(
        aksjonspunktStream.healthy,
        k9KlageStream.healthy,
        aksjonspunkTilbakeStream.healthy,
        aksjonspunkPunsjStream.healthy,
        ungSakStream.healthy,
        ungTilbakeStream.healthy,
    )

    private val isReadyChecks = setOf(
        aksjonspunktStream.ready,
        k9KlageStream.ready,
        aksjonspunkTilbakeStream.ready,
        aksjonspunkPunsjStream.ready,
        ungSakStream.ready,
        ungTilbakeStream.ready,
    )

    internal fun stop() {
        logger.info("Stopper streams.")
        aksjonspunktStream.stop()
        aksjonspunkTilbakeStream.stop()
        aksjonspunkPunsjStream.stop()
        ungSakStream.stop()
        ungTilbakeStream.stop()
        logger.info("Alle streams stoppet.")
    }

    internal fun isReadyChecks() = isReadyChecks
    internal fun isHealtyChecks() = healthChecks
}
