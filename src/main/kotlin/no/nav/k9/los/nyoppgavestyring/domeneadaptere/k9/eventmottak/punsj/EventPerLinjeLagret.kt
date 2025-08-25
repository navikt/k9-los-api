package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj

import java.time.LocalDateTime

data class EventPerLinjeLagret(
    val id: Long,
    val eksternId: String,
    val eksternVersjon: String,
    val eventNrForOppgave: Int,
    val eventDto: PunsjEventDto,
    val opprettet: LocalDateTime,
)