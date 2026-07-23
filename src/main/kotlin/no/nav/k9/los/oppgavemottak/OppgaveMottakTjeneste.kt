package no.nav.k9.los.oppgavemottak

import kotliquery.TransactionalSession
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import no.nav.k9.los.oppgavemottak.original.OppgaveV3Tjeneste as OppgaveV3OriginalTjeneste

class OppgaveMottakTjeneste(
    private val oppgaveV3OriginalTjeneste: OppgaveV3OriginalTjeneste,
) {

    fun sjekkDuplikatOgProsesser(
        innsending: NyOppgaveVersjonInnsending,
        tx: TransactionalSession,
        forrigeOppgaveversjon: OppgaveV3? = null,
    ): OppgaveV3? {
        return when (innsending) {
            is NyOppgaveversjon -> {
                if (!oppgaveV3OriginalTjeneste.erNyEksternversjon(innsending.dto, tx)) return null
                oppgaveV3OriginalTjeneste.lagreNyOppgaveversjon(innsending.dto, tx, forrigeOppgaveversjon)
            }
            is VaskOppgaveversjon -> {
                if (oppgaveV3OriginalTjeneste.eksistererFraFør(innsending.dto.eksternId, innsending.dto.type, innsending.dto.område, tx)) {
                    oppgaveV3OriginalTjeneste.vaskEksisterendeOppgaveversjon(innsending.dto, innsending.eventNummer, tx, forrigeOppgaveversjon)
                } else {
                    oppgaveV3OriginalTjeneste.lagreNyOppgaveversjon(innsending.dto, tx, forrigeOppgaveversjon)
                }
            }
        }
    }

    fun hentOppgaveversjon(
        område: String,
        oppgavetype: String,
        eksternId: String,
        eksternVersjon: String,
        tx: TransactionalSession
    ): OppgaveV3 {
        return oppgaveV3OriginalTjeneste.hentOppgaveversjon(område, oppgavetype, eksternId, eksternVersjon, tx)
    }

    fun hentOppgaveversjon(
        område: String,
        oppgavetype: String,
        eksternId: String,
        internVersjon: Int,
        tx: TransactionalSession
    ): OppgaveV3? {
        return oppgaveV3OriginalTjeneste.hentOppgaveversjon(område, oppgavetype, eksternId, internVersjon, tx)
    }

    fun slettOppgave(oppgavenøkkel: OppgaveNøkkelDto, tx: TransactionalSession) {
        oppgaveV3OriginalTjeneste.slettOppgave(oppgavenøkkel, tx)
    }

    fun tellAntall(): Pair<Long, Long> {
        return oppgaveV3OriginalTjeneste.tellAntall()
    }
}