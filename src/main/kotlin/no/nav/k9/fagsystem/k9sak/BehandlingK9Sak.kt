package no.nav.k9.fagsystem.k9sak

import no.nav.k9.domene.lager.oppgave.v2.Behandling
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class BehandlingK9Sak(
    val behandling: Behandling,
    val fagsystemBehandlingdata: BehandlingdataK9Sak?,
) : Behandling(behandling, id = behandling.id)  {

    companion object {
        private val log = LoggerFactory.getLogger(BehandlingK9Sak::class.java)
    }

    override fun erFerdigstilt(): Boolean {
        return erFerdigstilt() && fagsystemBehandlingdata?.erFerdigstilt() == true
    }

    override fun ferdigstill(tidspunkt: LocalDateTime, ansvarligSaksbehandler: String?, enhet: String?) {
        super.ferdigstill(tidspunkt, ansvarligSaksbehandler = ansvarligSaksbehandler, enhet = enhet)
        fagsystemBehandlingdata?.ferdigstill(tidspunkt)
    }
}