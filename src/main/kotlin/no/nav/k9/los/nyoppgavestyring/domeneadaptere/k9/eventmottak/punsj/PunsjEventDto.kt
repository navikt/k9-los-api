package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventLagret
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.sak.typer.AktørId
import no.nav.k9.sak.typer.JournalpostId
import java.time.LocalDateTime
import java.util.*

typealias PunsjId = UUID

data class PunsjEventDto(
    val eksternId: PunsjId,
    val journalpostId: JournalpostId,
    val eventTid: LocalDateTime,
    val status: Oppgavestatus? = null,
    val aktørId: AktørId?,
    val aksjonspunktKoderMedStatusListe: MutableMap<String, String>,
    val pleietrengendeAktørId: String? = null,
    val type : String? = null,
    val ytelse : String? = null,
    val sendtInn : Boolean? = null,
    val ferdigstiltAv: String? = null,
    val journalførtTidspunkt: LocalDateTime? = null
) {
    fun safePrint() = """
        PunsjEventDto(eksternId=$eksternId, 
        journalpostId=$journalpostId, 
        eventTid=$eventTid, 
        aksjonspunktKoderMedStatusListe=$aksjonspunktKoderMedStatusListe, 
        type=$type,
        status=$status,
        ytelse=$ytelse, 
        sendtInn=$sendtInn,
        journalførtTidspunkt=$journalførtTidspunkt,
        ferdigstiltAv=$ferdigstiltAv)
        """.trimIndent()

    companion object {
        fun fraEventLagret(eventLagret: EventLagret): PunsjEventDto {
            if (eventLagret.fagsystem != Fagsystem.PUNSJ) {
                throw IllegalStateException()
            }
            return LosObjectMapper.instance.readValue<PunsjEventDto>(eventLagret.eventJson)
        }
    }
}