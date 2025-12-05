package no.nav.k9.los.nyoppgavestyring.forvaltning

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventDto
import no.nav.k9.sak.typer.JournalpostId
import java.time.LocalDateTime

data class K9PunsjEventIkkeSensitiv(
    val eksternId: String,
    val journalpostId: JournalpostId,
    val eventTid: LocalDateTime,
    val aksjonspunktKoderMedStatusListe: Map<String, String>,
    val type : String?,
    val ytelse : String?,
    val sendtInn : Boolean?,
    val ferdigstiltAv: String?,
) {
    constructor(event: K9PunsjEventDto) : this(
        eksternId = event.eksternId.toString(),
        journalpostId = event.journalpostId,
        eventTid = event.eventTid,
        aksjonspunktKoderMedStatusListe = event.aksjonspunktKoderMedStatusListe,
        type = event.type,
        ytelse = event.ytelse,
        sendtInn = event.sendtInn,
        ferdigstiltAv = event.ferdigstiltAv,
    )
}
