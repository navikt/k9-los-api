package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager

class OppgaveTjeneste(
    private val oppgaveRepository: OppgaveRepository,
    private val transactionalManager: TransactionalManager
) {
    fun hentOppgave(eksternId: String) : Oppgave {
        return transactionalManager.transaction { tx ->
            oppgaveRepository.hentNyesteOppgaveForEksternId(tx, eksternId)
        }
    }

    fun hentÅpneOppgaverForReservasjonsnøkkel(reservasjonsnøkkel: String) : List<Oppgave> {
        return transactionalManager.transaction { tx ->
            oppgaveRepository.hentAlleÅpneOppgaverForReservasjonsnøkkel(tx, reservasjonsnøkkel)
        }
    }
}