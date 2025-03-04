package no.nav.k9.los.integrasjon.kafka

import no.nav.k9.los.Configuration
import no.nav.k9.los.aksjonspunktbehandling.*
import org.slf4j.LoggerFactory

internal class AsynkronProsesseringV1Service(
    kafkaConfig: KafkaConfig,
    kafkaAivenConfig: IKafkaConfig,
    configuration: Configuration,
    k9sakEventHandler: K9sakEventHandler,
    k9KlageEventHandler: K9KlageEventHandler,
    k9TilbakeEventHandler: K9TilbakeEventHandler,
    punsjEventHandler: K9punsjEventHandler,
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(AsynkronProsesseringV1Service::class.java)
    }

    private val aksjonspunktStream = AksjonspunktStreamK9(
        kafkaConfig = if (configuration.k9SakConsumerAiven()) kafkaAivenConfig else kafkaConfig,
        configuration = configuration,
        k9sakEventHandler = k9sakEventHandler
    )

    private val k9KlageStream = AksjonspunktKlageStream(
        kafkaConfig = kafkaAivenConfig,
        configuration = configuration,
        k9KlageEventHandler = k9KlageEventHandler,
    )

    private val aksjonspunkTilbaketStream = AksjonspunktTilbakeStream(
        kafkaConfig = if (configuration.tilbakeConsumerAiven()) kafkaAivenConfig else kafkaConfig,
        configuration = configuration,
        k9TilbakeEventHandler = k9TilbakeEventHandler
    )

    private val aksjonspunkPunsjStream = AksjonspunktPunsjStream(
        kafkaConfig = if (configuration.punsjConsumerAiven()) kafkaAivenConfig else kafkaConfig,
        configuration = configuration,
        K9punsjEventHandler = punsjEventHandler
    )

    private val healthChecks = setOf(
        aksjonspunktStream.healthy,
        k9KlageStream.healthy,
        aksjonspunkTilbaketStream.healthy,
        aksjonspunkPunsjStream.healthy
    )

    private val isReadyChecks = setOf(
        aksjonspunktStream.ready,
        k9KlageStream.ready,
        aksjonspunkTilbaketStream.ready,
        aksjonspunkPunsjStream.ready
    )

    internal fun stop() {
        logger.info("Stopper streams.")
        aksjonspunktStream.stop()
        aksjonspunkTilbaketStream.stop()
        aksjonspunkPunsjStream.stop()
        logger.info("Alle streams stoppet.")
    }

    internal fun isReadyChecks() = isReadyChecks
    internal fun isHealtyChecks() = healthChecks
}
