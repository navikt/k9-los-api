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
        val hentOppgaveMedVersjoner = System.currentTimeMillis()
        val oppgaveMedHistoriskeVersjoner =
            oppgaveRepository.hentOppgaveMedHistoriskeVersjoner(tx, id)
        log.info("Hentet oppgave med historiske versjoner: $id, tidsbruk: ${System.currentTimeMillis() - hentOppgaveMedVersjoner}")
        val behandling = OppgaveTilBehandlingMapper().lagBehandling(oppgaveMedHistoriskeVersjoner)
        val sak = OppgaveTilSakMapper().lagSak(oppgaveMedHistoriskeVersjoner)
        return Pair(sak, behandling)
    }

}