package no.nav.k9.los.forvaltning

import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.Oppgavefelt
import java.time.LocalDateTime

data class OppgaveDto(
    val eksternId: String,
    val eksternVersjon: String,
    val reservasjonsnøkkel: String,
    val status: Oppgavestatus,
    val endretTidspunkt: LocalDateTime,
    val felter: List<Oppgavefelt>,
) {
    constructor(oppgave: Oppgave) : this(
        eksternId = oppgave.eksternId,
        eksternVersjon = oppgave.eksternVersjon,
        reservasjonsnøkkel = oppgave.reservasjonsnøkkel,
        status = oppgave.status,
        endretTidspunkt = oppgave.endretTidspunkt,
        felter = oppgave.felter,
    )
}