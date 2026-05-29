package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import kotliquery.Row
import java.time.LocalDateTime

internal data class OppgaveRad(
    val id: Long,
    val oppgavetypeEksternId: String,
    val oppgaveEksternId: String,
    val oppgaveEksternVersjon: String,
    val oppgavestatus: String,
    val endretTidspunkt: LocalDateTime,
    val reservasjonsnokkel: String,
) {
    companion object {
        internal fun Row.tilOppgaveRad() = OppgaveRad(
            id = this.long("id"),
            oppgavetypeEksternId = this.string("oppgavetype_ekstern_id"),
            oppgaveEksternId = this.string("oppgave_ekstern_id"),
            oppgaveEksternVersjon = this.string("oppgave_ekstern_versjon"),
            oppgavestatus = this.string("oppgavestatus"),
            endretTidspunkt = this.localDateTime("endret_tidspunkt"),
            reservasjonsnokkel = this.string("reservasjonsnokkel"),
        )
    }
}
