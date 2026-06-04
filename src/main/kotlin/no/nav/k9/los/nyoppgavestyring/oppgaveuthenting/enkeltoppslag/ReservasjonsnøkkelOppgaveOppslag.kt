package no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.enkeltoppslag

import kotliquery.TransactionalSession
import no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.Oppgave

interface ReservasjonsnøkkelOppgaveOppslag {
    fun hentÅpneOppgaverForReservasjonsnøkkel(reservasjonsnøkkel: String): List<Oppgave>
    fun hentÅpneOppgaverForReservasjonsnøkkel(reservasjonsnøkkel: String, tx: TransactionalSession): List<Oppgave>
}
