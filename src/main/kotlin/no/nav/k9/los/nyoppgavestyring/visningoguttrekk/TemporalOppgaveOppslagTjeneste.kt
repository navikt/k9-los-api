package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import kotliquery.TransactionalSession
import java.time.LocalDateTime

interface TemporalOppgaveOppslagTjeneste {
    fun hentTidsserie(oppgavetypeEksternId: String, eksternId: String): List<Oppgave>
    fun hentTidsserie(oppgavetypeEksternId: String, eksternId: String, tx: TransactionalSession): List<Oppgave>
    fun hentOppgaveForTidspunkt(oppgavetypeEksternId: String, eksternId: String, tidspunkt: LocalDateTime): Oppgave?
    fun hentOppgaveForTidspunkt(oppgavetypeEksternId: String, eksternId: String, tidspunkt: LocalDateTime, tx: TransactionalSession): Oppgave?
}
