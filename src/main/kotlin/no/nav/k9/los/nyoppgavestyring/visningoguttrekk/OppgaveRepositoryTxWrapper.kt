package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
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

    fun hentOppgaveTidsserie(områdeEksternId: String, oppgaveTypeEksternId: String, oppgaveEksternId: String): List<Oppgave> {
        return transactionalManager.transaction { tx ->
            oppgaveRepository.hentOppgaveTidsserie(
                områdeEksternId = områdeEksternId,
                oppgaveTypeEksternId = oppgaveTypeEksternId,
                oppgaveEksternId = oppgaveEksternId,
                tx = tx)
        }
    }

    fun hentÅpneOppgaverForReservasjonsnøkkel(reservasjonsnøkkel: String): List<Oppgave> {
        return transactionalManager.transaction { tx ->
            oppgaveRepository.hentAlleÅpneOppgaverForReservasjonsnøkkel(tx, reservasjonsnøkkel)
        }
    }

    fun hentOppgaverPaget(eksternoppgaveIder: List<EksternOppgaveId>, antallPrPage: Int, pageNr: Int): List<Oppgave> {
        if (antallPrPage * pageNr >= eksternoppgaveIder.size) { return emptyList() }
        val fra = antallPrPage * pageNr
        val til = (fra + antallPrPage).takeIf { it < eksternoppgaveIder.size } ?: eksternoppgaveIder.size

        return transactionalManager.transaction { tx ->
            eksternoppgaveIder.subList(fra, til).map { eksternoppgaveId ->
                oppgaveRepository.hentNyesteOppgaveForEksternId(tx, eksternoppgaveId.område, eksternoppgaveId.eksternId)
            }
        }
    }
}