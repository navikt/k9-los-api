package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager

class OppgaveRepositoryTxWrapper(
    private val oppgaveRepository: OppgaveRepository,
    private val transactionalManager: TransactionalManager
) {
    fun hentOppgave(eksternId: String): Oppgave {
        return transactionalManager.transaction { tx ->
            oppgaveRepository.hentNyesteOppgaveForEksternId(tx, eksternId)
        }
    }

    fun hentÅpneOppgaverForReservasjonsnøkkel(reservasjonsnøkkel: String): List<Oppgave> {
        return transactionalManager.transaction { tx ->
            oppgaveRepository.hentAlleÅpneOppgaverForReservasjonsnøkkel(tx, reservasjonsnøkkel)
        }
    }

    fun hentOppgaverPaget(oppgaveEksternIder: List<String>, antallPrPage: Int, pageNr: Int): List<Oppgave> {
        if (antallPrPage * pageNr >= oppgaveEksternIder.size) { return emptyList() }
        val fra = antallPrPage * pageNr
        val til = (fra + antallPrPage).takeIf { it < oppgaveEksternIder.size } ?: oppgaveEksternIder.size

        return transactionalManager.transaction { tx ->
            oppgaveEksternIder.subList(fra, til).map { oppgaveEksternId ->
                oppgaveRepository.hentNyesteOppgaveForEksternId(tx, oppgaveEksternId)
            }
        }
    }
}