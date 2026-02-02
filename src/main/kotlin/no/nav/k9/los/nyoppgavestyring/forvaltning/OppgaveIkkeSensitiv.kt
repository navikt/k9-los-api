package no.nav.k9.los.nyoppgavestyring.forvaltning

import io.ktor.util.*
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgavefelt
import java.time.LocalDateTime

data class OppgaveIkkeSensitiv(
    val eksternId: String,
    val eksternVersjon: String,
    val oppgavetypeEksternId: String,
    val områdeEksternId: String,
    val status: String, //TODO: definere typer/enum
    val endretTidspunkt: LocalDateTime,
    val felter: List<Oppgavefelt>
) {
    constructor(oppgave: Oppgave) : this(
        eksternId = oppgave.eksternId,
        eksternVersjon = oppgave.eksternVersjon,
        oppgavetypeEksternId = oppgave.oppgavetype.eksternId,
        områdeEksternId = oppgave.oppgavetype.område.eksternId,
        status = oppgave.status,
        endretTidspunkt = oppgave.endretTidspunkt,
        felter = oppgave.felter.filterNot { it.eksternId.lowercase().contains("aktor") }
    )
}