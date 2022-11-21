package no.nav.k9.los.aksjonspunktbehandling

import no.nav.k9.los.domene.modell.K9KlageModell
import no.nav.k9.los.domene.repository.BehandlingProsessEventKlageRepository
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventKlageDto
import no.nav.k9.los.domene.modell.FagsakYtelseType
import org.slf4j.LoggerFactory


class K9KlageEventHandler constructor(
    private val behandlingProsessEventKlageRepository: BehandlingProsessEventKlageRepository,
) {
    private val log = LoggerFactory.getLogger(K9KlageEventHandler::class.java)

    private val tillatteYtelseTyper = listOf(
        FagsakYtelseType.OMSORGSPENGER,
        FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
        FagsakYtelseType.OMSORGSPENGER_KS,
        FagsakYtelseType.OMSORGSPENGER_MA,
        FagsakYtelseType.OMSORGSPENGER_AO,
        FagsakYtelseType.OMSORGSDAGER,
        FagsakYtelseType.PPN
    )

    fun prosesser(
        event: BehandlingProsessEventKlageDto
    ) {
        behandlingProsessEventKlageRepository.lagre(event.eksternId!!) { k9KlageModell ->
            if (k9KlageModell == null) {
                return@lagre K9KlageModell(mutableListOf(event))
            }
            if (k9KlageModell.eventer.contains(event)) {
                log.info("""Skipping eventen har kommet tidligere ${event.eventTid}""")
                return@lagre k9KlageModell
            }
            k9KlageModell.eventer.add(event)
            k9KlageModell
        }
    }
}
