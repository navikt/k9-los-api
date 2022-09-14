package no.nav.k9.nyoppgavestyring.adaptere.statistikkadapter

import kotliquery.TransactionalSession
import no.nav.k9.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import no.nav.k9.statistikk.kontrakter.Behandling

class OppgavestatistikkTjeneste(
    private val oppgaveTilBehandlingAdapter: OppgaveTilBehandlingAdapter,
    private val oppgaveRepository: OppgaveRepository,
) {

    //TODO: koble fra transaksjon? Dette burde ikke kjøre i samme tx som lagring av oppgave, tror jeg
    fun byggOppgavestatistikkForBehandling(eksternOppgaveId: String, tx: TransactionalSession): Behandling {
        val oppgaveMedHistoriskeVersjoner =
            oppgaveRepository.hentOppgaveMedHistoriskeVersjoner(tx, eksternOppgaveId)
        val behandling = oppgaveTilBehandlingAdapter.lagBehandling(oppgaveMedHistoriskeVersjoner)
        TODO()
    }
}