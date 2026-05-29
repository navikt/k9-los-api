package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import kotliquery.TransactionalSession

interface ReservasjonsnøkkelOppgaveTjeneste {
    /** Hent alle åpne oppgaver for en gitt reservasjonsnøkkel */
    fun hentÅpneOppgaverForReservasjonsnøkkel(reservasjonsnøkkel: String): List<Oppgave>

    /** Hent alle åpne oppgaver for en gitt reservasjonsnøkkel innenfor callers transaksjon */
    fun hentÅpneOppgaverForReservasjonsnøkkel(reservasjonsnøkkel: String, tx: TransactionalSession): List<Oppgave>
}
