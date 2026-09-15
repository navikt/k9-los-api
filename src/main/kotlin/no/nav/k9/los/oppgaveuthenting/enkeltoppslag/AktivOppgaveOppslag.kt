package no.nav.k9.los.oppgaveuthenting.enkeltoppslag

import kotliquery.TransactionalSession
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave

interface AktivOppgaveOppslag {
    fun hentAktivOppgave(område: Områder, eksternId: String, oppgavetypeEksternId: String): Oppgave
    fun hentAktivOppgave(område: Områder, eksternId: String, oppgavetypeEksternId: String, tx: TransactionalSession): Oppgave
    fun hentAktivOppgaveHvisFinnes(område: Områder, eksternId: String, oppgavetypeEksternId: String): Oppgave?
    fun hentAktivOppgaveHvisFinnes(område: Områder, eksternId: String, oppgavetypeEksternId: String, tx: TransactionalSession): Oppgave?
}
