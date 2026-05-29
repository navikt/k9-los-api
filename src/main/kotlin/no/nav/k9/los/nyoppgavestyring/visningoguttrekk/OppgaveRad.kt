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
    constructor(row: Row) : this(
        id = row.long("id"),
        oppgavetypeEksternId = row.string("oppgavetype_ekstern_id"),
        oppgaveEksternId = row.string("oppgave_ekstern_id"),
        oppgaveEksternVersjon = row.string("oppgave_ekstern_versjon"),
        oppgavestatus = row.string("oppgavestatus"),
        endretTidspunkt = row.localDateTime("endret_tidspunkt"),
        reservasjonsnokkel = row.string("reservasjonsnokkel"),
    )
}
