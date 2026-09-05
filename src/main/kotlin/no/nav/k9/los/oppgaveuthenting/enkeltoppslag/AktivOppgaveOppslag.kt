package no.nav.k9.los.oppgaveuthenting.enkeltoppslag

import kotliquery.TransactionalSession
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave

interface AktivOppgaveOppslag {
    fun hentAktivOppgave(eksternId: String, oppgavetypeEksternId: String, område: Områder): Oppgave
    fun hentAktivOppgave(eksternId: String, oppgavetypeEksternId: String, område: Områder, tx: TransactionalSession): Oppgave
    fun hentAktivOppgaveHvisFinnes(eksternId: String, oppgavetypeEksternId: String, område: Områder): Oppgave?
    fun hentAktivOppgaveHvisFinnes(eksternId: String, oppgavetypeEksternId: String, område: Områder, tx: TransactionalSession): Oppgave?
}
