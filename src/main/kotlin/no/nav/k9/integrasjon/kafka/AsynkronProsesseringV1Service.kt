package no.nav.k9.integrasjon.kafka

import no.nav.k9.Configuration
import no.nav.k9.aksjonspunktbehandling.*
import no.nav.k9.fagsystem.k9sak.K9SakStream
import no.nav.k9.fagsystem.k9sak.K9sakEventHandlerV2
import org.slf4j.LoggerFactory

internal class AsynkronProsesseringV1Service(
    kafkaConfig: KafkaConfig,
    kafkaAivenConfig: IKafkaConfig,
    configuration: Configuration,
    k9sakEventHandler: K9sakEventHandler,
    k9sakEventHandlerv2: K9sakEventHandlerV2,
    k9TilbakeEventHandler: K9TilbakeEventHandler,
    punsjEventHandler: K9punsjEventHandler,
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(AsynkronProsesseringV1Service::class.java)
    }

    private val aksjonspunktStream = AksjonspunktStreamK9(
        kafkaConfig = kafkaConfig,
        configuration = configuration,
        k9sakEventHandler = k9sakEventHandler
    )

    private val k9SakStream = K9SakStream(
        kafkaConfig = kafkaAivenConfig,
        configuration = configuration,
        k9sakEventHandlerv2 = k9sakEventHandlerv2
    )

    private val aksjonspunkTilbaketStream = AksjonspunktTilbakeStream(
        kafkaConfig = kafkaConfig,
        configuration = configuration,
        k9TilbakeEventHandler = k9TilbakeEventHandler
    )

    private val aksjonspunkPunsjStream = AksjonspunktPunsjStream(
        kafkaConfig = kafkaConfig,
        configuration = configuration,
        K9punsjEventHandler = punsjEventHandler
    )

    private val healthChecks = setOf(
        k9SakStream.healthy,
        aksjonspunktStream.healthy,
        aksjonspunkTilbaketStream.healthy,
        aksjonspunkPunsjStream.healthy
    )

    private val isReadyChecks = setOf(
        k9SakStream.ready,
        aksjonspunktStream.ready,
        aksjonspunkTilbaketStream.ready,
        aksjonspunkPunsjStream.ready
    )

    internal fun stop() {
        logger.info("Stopper streams.")
        aksjonspunktStream.stop()
        k9SakStream.stop()
        aksjonspunkTilbaketStream.stop()
        aksjonspunkPunsjStream.stop()
        logger.info("Alle streams stoppet.")
    }

    internal fun isReadyChecks() = isReadyChecks
    internal fun isHealtyChecks() = healthChecks
}
