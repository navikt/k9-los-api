package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventperlinje.punsj

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjEventDto
import java.time.LocalDateTime

data class EventLagret(
    val id: Long,
    val eksternId: String,
    val eksternVersjon: String,
    val eventNrForOppgave: Int,
    val eventDto: PunsjEventDto,
    val opprettet: LocalDateTime,
)