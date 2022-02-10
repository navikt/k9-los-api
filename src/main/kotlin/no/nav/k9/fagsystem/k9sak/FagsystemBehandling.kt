package no.nav.k9.fagsystem.k9sak

import no.nav.k9.domene.lager.oppgave.v2.Behandling
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class FagsystemBehandling(
    behandling: Behandling,
    val fagsystemBehandlingdata: FagsystemBehandlingData?,
) : Behandling(behandling, id = behandling.id)  {

    companion object {
        private val log = LoggerFactory.getLogger(FagsystemBehandling::class.java)
    }

    override fun erFerdigstilt(): Boolean {
        return erFerdigstilt() && fagsystemBehandlingdata?.erFerdigstilt() == true
    }

    fun ferdigstill(tidspunkt: LocalDateTime, ansvarligSaksbehandler: String?, enhet: String?) {
        log.info("Ferdigstiller behandling $eksternReferanse")
        super.lukkAktiveOppgaver(tidspunkt, ansvarligSaksbehandler = ansvarligSaksbehandler, enhet = enhet)
        fagsystemBehandlingdata?.ferdigstill(tidspunkt)
    }
}