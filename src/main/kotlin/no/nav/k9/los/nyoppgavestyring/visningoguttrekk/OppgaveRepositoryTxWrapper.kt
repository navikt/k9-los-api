package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import java.time.LocalDateTime

class OppgaveRepositoryTxWrapper(
    private val oppgaveRepository: OppgaveRepository,
    private val transactionalManager: TransactionalManager
) {
    fun hentOppgave(kildeområde: String, eksternId: String): Oppgave {
        return transactionalManager.transaction { tx ->
            oppgaveRepository.hentNyesteOppgaveForEksternId(tx, kildeområde, eksternId, LocalDateTime.now())
        }
    }

    fun hentÅpneOppgaverForReservasjonsnøkkel(reservasjonsnøkkel: String): List<Oppgave> {
        return transactionalManager.transaction { tx ->
            oppgaveRepository.hentAlleÅpneOppgaverForReservasjonsnøkkel(tx, reservasjonsnøkkel)
        }
    }

    fun hentOppgaver(eksternoppgaveIder: List<EksternOppgaveId>): List<Oppgave> {
        return transactionalManager.transaction { tx ->
            eksternoppgaveIder.map { eksternoppgaveId ->
                oppgaveRepository.hentNyesteOppgaveForEksternId(tx, eksternoppgaveId.område, eksternoppgaveId.eksternId)
            }
        }
    }
}