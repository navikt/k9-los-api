package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottakoghistorikk.punsj

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjEventDto
import java.time.LocalDateTime

data class EventLagret(
    val id: Long,
    val eksternId: String,
    val eksternVersjon: String,
    val eventNrForOppgave: Int,
    val eventV3Dto: PunsjEventDto,
    val opprettet: LocalDateTime,
)