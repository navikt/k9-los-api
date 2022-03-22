package no.nav.k9.integrasjon.kafka.dto

import no.nav.k9.sak.typer.AktørId
import no.nav.k9.sak.typer.JournalpostId
import java.time.LocalDateTime
import java.util.*

typealias PunsjId = UUID

data class PunsjEventDto(
    val eksternId: PunsjId,
    val journalpostId: JournalpostId,
    val eventTid: LocalDateTime,
    val aktørId: AktørId?,
    val aksjonspunktKoderMedStatusListe: MutableMap<String, String>,
    val pleietrengendeAktørId: String? = null,
    val type : String? = null,
    val ytelse : String? = null,
    val sendtInn : Boolean? = null,
    val ferdigstiltAv: String? = null,
) {
    fun safePrint() = """
        PunsjEventDto(eksternId=$eksternId, 
        journalpostId=$journalpostId, 
        eventTid=$eventTid, 
        aksjonspunktKoderMedStatusListe=$aksjonspunktKoderMedStatusListe, 
        type=$type, 
        ytelse=$ytelse, 
        sendtInn=$sendtInn,
        ferdigstiltAv=$ferdigstiltAv)
        """.trimIndent()
}