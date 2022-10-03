package no.nav.k9.nyoppgavestyring.domeneadaptere.statistikk

import kotliquery.TransactionalSession
import no.nav.k9.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.slf4j.LoggerFactory

class OppgavestatistikkTjeneste(
    private val oppgaveRepository: OppgaveRepository,
    private val statistikkPublisher: StatistikkPublisher
) {

    private val log = LoggerFactory.getLogger(OppgavestatistikkTjeneste::class.java)

    fun sendStatistikk(id: String, tx: TransactionalSession) {
        val (sak, behandling) = byggOppgavestatistikk(id, tx)
        statistikkPublisher.publiser(sak, behandling)
    }

    //TODO: koble fra transaksjon? Dette burde ikke kjøre i samme tx som lagring av oppgave, tror jeg
    private fun byggOppgavestatistikk(id: String, tx: TransactionalSession): Pair<Sak, Behandling> {
        val hentOppgave = System.currentTimeMillis()
        val oppgave = oppgaveRepository.hentOppgave(tx, id)
        log.info("Hentet oppgave med historiske versjoner: $id, tidsbruk: ${System.currentTimeMillis() - hentOppgave}")
        val behandling = OppgaveTilBehandlingMapper().lagBehandling(oppgave)
        val sak = OppgaveTilSakMapper().lagSak(oppgave)
        return Pair(sak, behandling)
    }

}