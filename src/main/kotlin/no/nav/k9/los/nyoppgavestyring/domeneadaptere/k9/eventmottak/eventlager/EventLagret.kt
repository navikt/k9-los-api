package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager

import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import java.time.LocalDateTime

data class EventLagret(
    val id: Long,
    val fagsystem: Fagsystem,
    val eksternId: String,
    val eksternVersjon: String,
    val eventJson: String,
    val opprettet: LocalDateTime,
) {
}
