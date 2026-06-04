package no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.enkeltoppslag

import kotliquery.TransactionalSession
import no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.Oppgave
import java.time.LocalDateTime

interface TemporalOppgaveOppslag {
    fun hentTidsserie(oppgavetypeEksternId: String, oppgaveEksternId: String): List<Oppgave>
    fun hentTidsserie(oppgavetypeEksternId: String, eksternId: String, tx: TransactionalSession): List<Oppgave>
    fun hentOppgaveForTidspunkt(oppgavetypeEksternId: String, eksternId: String, tidspunkt: LocalDateTime): Oppgave?
    fun hentOppgaveForTidspunkt(oppgavetypeEksternId: String, eksternId: String, tidspunkt: LocalDateTime, tx: TransactionalSession): Oppgave?
}
