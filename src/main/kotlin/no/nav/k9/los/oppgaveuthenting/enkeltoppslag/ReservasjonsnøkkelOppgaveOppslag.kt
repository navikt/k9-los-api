package no.nav.k9.los.oppgaveuthenting.enkeltoppslag

import kotliquery.TransactionalSession
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave

interface ReservasjonsnøkkelOppgaveOppslag {
    fun hentÅpneOppgaverForReservasjonsnøkkel(område: Områder, reservasjonsnøkkel: String): List<Oppgave>
    fun hentÅpneOppgaverForReservasjonsnøkkel(område: Områder, reservasjonsnøkkel: String, tx: TransactionalSession): List<Oppgave>
}
