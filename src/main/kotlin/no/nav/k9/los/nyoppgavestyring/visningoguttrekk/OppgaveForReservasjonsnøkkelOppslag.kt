package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import kotliquery.TransactionalSession

interface OppgaveForReservasjonsnøkkelOppslag {
    fun hentÅpneOppgaverForReservasjonsnøkkel(reservasjonsnøkkel: String): List<Oppgave>
    fun hentÅpneOppgaverForReservasjonsnøkkel(reservasjonsnøkkel: String, tx: TransactionalSession): List<Oppgave>
}
