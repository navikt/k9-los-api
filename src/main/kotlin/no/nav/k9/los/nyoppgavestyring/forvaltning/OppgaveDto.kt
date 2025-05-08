package no.nav.k9.los.nyoppgavestyring.forvaltning

import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgavefelt
import java.time.LocalDateTime

data class OppgaveDto(
    val eksternId: String,
    val eksternVersjon: String,
    val reservasjonsnøkkel: String,
    val status: String,
    val endretTidspunkt: LocalDateTime,
    val felter: List<Oppgavefelt>,
    val versjon: Int,
) {
    constructor(oppgave: Oppgave) : this(
        eksternId = oppgave.eksternId,
        eksternVersjon = oppgave.eksternVersjon,
        reservasjonsnøkkel = oppgave.reservasjonsnøkkel,
        status = oppgave.status,
        endretTidspunkt = oppgave.endretTidspunkt,
        felter = oppgave.felter,
        versjon = oppgave.versjon
    )
}