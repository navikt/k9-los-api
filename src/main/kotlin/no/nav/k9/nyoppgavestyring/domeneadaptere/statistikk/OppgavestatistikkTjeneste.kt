package no.nav.k9.nyoppgavestyring.domeneadaptere.statistikk

import kotliquery.TransactionalSession
import no.nav.k9.nyoppgavestyring.visningoguttrekk.OppgaveRepository

class OppgavestatistikkTjeneste(
    private val oppgaveRepository: OppgaveRepository,
    private val statistikkPublisher: StatistikkPublisher
) {

    fun sendStatistikk(id: String, tx: TransactionalSession) {
        val (sak, behandling) = byggOppgavestatistikk(id, tx)
        statistikkPublisher.publiser(sak, behandling)
    }

    //TODO: koble fra transaksjon? Dette burde ikke kjøre i samme tx som lagring av oppgave, tror jeg
    private fun byggOppgavestatistikk(id: String, tx: TransactionalSession): Pair<Sak, Behandling> {
        val oppgaveMedHistoriskeVersjoner =
            oppgaveRepository.hentOppgaveMedHistoriskeVersjoner(tx, id)
        val behandling = OppgaveTilBehandlingMapper().lagBehandling(oppgaveMedHistoriskeVersjoner)
        val sak = OppgaveTilSakMapper().lagSak(oppgaveMedHistoriskeVersjoner)
        return Pair(sak, behandling)
    }

}