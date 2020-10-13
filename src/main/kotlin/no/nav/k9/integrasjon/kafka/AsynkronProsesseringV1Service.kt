package no.nav.k9.integrasjon.kafka

import io.ktor.util.*
import no.nav.k9.Configuration
import no.nav.k9.aksjonspunktbehandling.*
import org.slf4j.LoggerFactory

internal class AsynkronProsesseringV1Service @KtorExperimentalAPI constructor(
    kafkaConfig: KafkaConfig,
    configuration: Configuration,
    k9sakEventHandler: K9sakEventHandler,
    k9TilbakeEventHandler: K9TilbakeEventHandler,
    punsjEventHandler: K9punsjEventHandler    
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(AsynkronProsesseringV1Service::class.java)
    }


    @KtorExperimentalAPI
    private val aksjonspunktStream = AksjonspunktStreamK9(
        kafkaConfig = kafkaConfig,
        configuration = configuration,
        k9sakEventHandler = k9sakEventHandler
    )

    @KtorExperimentalAPI
    private val aksjonspunkTilbaketStream = AksjonspunktTilbakeStream(
        kafkaConfig = kafkaConfig,
        configuration = configuration,
        k9TilbakeEventHandler = k9TilbakeEventHandler
    )
    
    @KtorExperimentalAPI
    private val aksjonspunkPunsjStream = AksjonspunktPunsjStream(
        kafkaConfig = kafkaConfig,
        configuration = configuration,
        K9punsjEventHandler = punsjEventHandler
    )

    @KtorExperimentalAPI
    private val healthChecks = setOf(
        aksjonspunktStream.healthy,
        aksjonspunkTilbaketStream.healthy,
        aksjonspunkPunsjStream.healthy
    )

    @KtorExperimentalAPI
    private val isReadyChecks = setOf(
        aksjonspunktStream.ready,
        aksjonspunkTilbaketStream.ready,
        aksjonspunkPunsjStream.ready
    )

    @KtorExperimentalAPI
    internal fun stop() {
        logger.info("Stopper streams.")
        aksjonspunktStream.stop()
        aksjonspunkTilbaketStream.stop()
        aksjonspunkPunsjStream.stop()
        logger.info("Alle streams stoppet.")
    }

    @KtorExperimentalAPI
    internal fun healthChecks() = healthChecks

    @KtorExperimentalAPI
    internal fun isReadyChecks() = isReadyChecks
}