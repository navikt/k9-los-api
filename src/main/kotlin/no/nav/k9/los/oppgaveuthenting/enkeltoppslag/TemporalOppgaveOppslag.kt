package no.nav.k9.los.oppgaveuthenting.enkeltoppslag

import kotliquery.TransactionalSession
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import java.time.LocalDateTime

interface TemporalOppgaveOppslag {
    fun hentTidsserie(område: Områder, oppgavetypeEksternId: String, oppgaveEksternId: String): List<Oppgave>
    fun hentTidsserie(område: Områder, oppgavetypeEksternId: String, eksternId: String, tx: TransactionalSession): List<Oppgave>
    fun hentOppgaveForTidspunkt(område: Områder, oppgavetypeEksternId: String, eksternId: String, tidspunkt: LocalDateTime): Oppgave?
    fun hentOppgaveForTidspunkt(område: Områder, oppgavetypeEksternId: String, eksternId: String, tidspunkt: LocalDateTime, tx: TransactionalSession): Oppgave?
}
