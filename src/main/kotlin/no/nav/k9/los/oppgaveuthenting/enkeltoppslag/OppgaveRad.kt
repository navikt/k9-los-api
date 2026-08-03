package no.nav.k9.los.oppgaveuthenting.enkeltoppslag

import kotliquery.Row
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import java.time.LocalDateTime

internal data class OppgaveRad(
    val id: Long,
    val oppgavetypeEksternId: String,
    val oppgaveEksternId: String,
    val oppgaveEksternVersjon: String,
    val oppgavestatus: Oppgavestatus,
    val endretTidspunkt: LocalDateTime,
    val reservasjonsnokkel: String,
) {
    companion object {
        internal fun Row.tilOppgaveRad() = OppgaveRad(
            id = this.long("id"),
            oppgavetypeEksternId = this.string("oppgavetype_ekstern_id"),
            oppgaveEksternId = this.string("oppgave_ekstern_id"),
            oppgaveEksternVersjon = this.string("oppgave_ekstern_versjon"),
            oppgavestatus = Oppgavestatus.fraKode(this.string("oppgavestatus")),
            endretTidspunkt = this.localDateTime("endret_tidspunkt"),
            reservasjonsnokkel = this.string("reservasjonsnokkel"),
        )
    }
}
