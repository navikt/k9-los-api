package no.nav.k9.los.nyoppgavestyring.uthenting

import kotliquery.TransactionalSession

interface ReservasjonsnøkkelOppgaveOppslag {
    fun hentÅpneOppgaverForReservasjonsnøkkel(reservasjonsnøkkel: String): List<Oppgave>
    fun hentÅpneOppgaverForReservasjonsnøkkel(reservasjonsnøkkel: String, tx: TransactionalSession): List<Oppgave>
}
