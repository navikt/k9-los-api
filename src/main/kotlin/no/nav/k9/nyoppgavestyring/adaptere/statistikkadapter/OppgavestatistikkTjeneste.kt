package no.nav.k9.nyoppgavestyring.adaptere.statistikkadapter

import kotliquery.TransactionalSession
import no.nav.k9.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import no.nav.k9.statistikk.kontrakter.Behandling
import no.nav.k9.statistikk.kontrakter.Sak

class OppgavestatistikkTjeneste(
    private val oppgaveTilBehandlingAdapter: OppgaveTilBehandlingAdapter,
    private val oppgaveTilSakAdapter: OppgaveTilSakAdapter,
    private val oppgaveRepository: OppgaveRepository,
) {

    fun sendStatistikk(id: String, tx: TransactionalSession) {
        val (behandlingEvent, sakEvent) = byggOppgavestatistikk(id, tx)
    }

    private fun sendEvent() {
        TODO("Not yet implemented")
    }

    fun byggOppgavestatistikk(id: String, tx: TransactionalSession): Pair<Behandling, Sak> {
        val oppgaveMedHistoriskeVersjoner =
            oppgaveRepository.hentOppgaveMedHistoriskeVersjoner(tx, id)
        val behandling = byggOppgavestatistikkForBehandling(oppgaveMedHistoriskeVersjoner)
        val sak = byggOppgavestatistikkForSak(oppgaveMedHistoriskeVersjoner)
        return Pair(behandling, sak)
    }

    //TODO: koble fra transaksjon? Dette burde ikke kjøre i samme tx som lagring av oppgave, tror jeg
    private fun byggOppgavestatistikkForBehandling(oppgaveMedHistoriskeVersjoner: Set<Oppgave>): Behandling {
        return oppgaveTilBehandlingAdapter.lagBehandling(oppgaveMedHistoriskeVersjoner)
    }

    private fun byggOppgavestatistikkForSak(oppgaveMedHistoriskeVersjoner: Set<Oppgave>): Sak {
        return oppgaveTilSakAdapter.lagSak(oppgaveMedHistoriskeVersjoner)
    }
}