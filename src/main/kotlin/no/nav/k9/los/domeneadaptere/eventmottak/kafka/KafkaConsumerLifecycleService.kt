package no.nav.k9.los.domeneadaptere.eventmottak.kafka

import no.nav.k9.los.Configuration
import no.nav.k9.los.domeneadaptere.eventmottak.k9.klage.K9KlageEventHandler
import no.nav.k9.los.domeneadaptere.eventmottak.k9.punsj.K9PunsjEventHandler
import no.nav.k9.los.domeneadaptere.eventmottak.k9.sak.K9SakEventHandler
import no.nav.k9.los.domeneadaptere.eventmottak.k9.tilbakekrav.K9TilbakeEventHandler
import no.nav.k9.los.domeneadaptere.eventmottak.ung.sak.UngSakEventHandler
import no.nav.k9.los.domeneadaptere.eventmottak.ung.tilbake.UngTilbakeEventHandler
import org.slf4j.LoggerFactory

internal class KafkaConsumerLifecycleService(
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
        private val logger = LoggerFactory.getLogger(KafkaConsumerLifecycleService::class.java)
    }

    // navn = consumer group-suffiks. Må ikke endres, ellers mistes committede offsets.
    private val konsumenter = listOf(
        EventKonsument(
            kafkaConfig = kafkaAivenConfig,
            navn = "AksjonspunktLagetV1",
            kilde = "k9sak",
            topic = configuration.getK9SakTopic(),
            antallTråder = 3, // topic har 3 partisjoner
            prosesser = k9sakEventHandler::prosesser,
        ),
        EventKonsument(
            kafkaConfig = kafkaAivenConfig,
            navn = "AksjonspunktLagetKlageV1",
            kilde = "k9klage",
            topic = configuration.getK9KlageTopic(),
            prosesser = k9KlageEventHandler::prosesser,
        ),
        EventKonsument(
            kafkaConfig = kafkaAivenConfig,
            navn = "TilbakeV1",
            kilde = "k9tilbake",
            topic = configuration.getK9TilbakeTopic(),
            prosesser = k9TilbakeEventHandler::prosesser,
        ),
        EventKonsument(
            kafkaConfig = kafkaAivenConfig,
            navn = "AksjonspunktLagetPunsjV1",
            kilde = "punsj",
            topic = configuration.getPunsjTopic(),
            sporingsfelt = "journalpostId",
            prosesser = k9PunsjEventHandler::prosesser,
        ),
        EventKonsument(
            kafkaConfig = kafkaAivenConfig,
            navn = "UngsakKafkaStream",
            kilde = "ungsak",
            topic = configuration.getUngSakHendelseTopic(),
            antallTråder = 3, // topic har 3 partisjoner
            prosesser = ungSakEventHandler::prosesser,
        ),
        EventKonsument(
            kafkaConfig = kafkaAivenConfig,
            navn = "UngTilbakeKafkaStream",
            kilde = "ung-tilbake",
            topic = configuration.getUngTilbakeHendelseTopic(),
            antallTråder = 3, // topic har 3 partisjoner
            prosesser = ungTilbakeEventHandler::prosesser,
        ),
    )

    internal fun stop() {
        logger.info("Stopper kafka-consumere.")
        konsumenter.forEach { it.stop() }
        logger.info("Alle kafka-consumere stoppet.")
    }

    internal fun isReadyChecks() = konsumenter.map { it.ready }.toSet()
    internal fun isHealtyChecks() = konsumenter.map { it.healthy }.toSet()
}

