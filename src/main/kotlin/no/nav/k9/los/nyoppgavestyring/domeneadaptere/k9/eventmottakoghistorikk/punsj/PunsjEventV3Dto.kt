package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottakoghistorikk.punsj

import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import java.time.LocalDateTime

@Deprecated("Importere kontrakt fra punsj når den blir tilgjengelig")
data class PunsjEventV3Dto(
    val eksternId: String, // Unik id i journalpostId
    val journalpostId: String,
    val aktørId: String?,
    val eventTid: LocalDateTime, // Brukes av LOS for å differensiere versjon, mappes til ekstern_versjon
    val aksjonspunktKoderMedStatusListe: Map<String, String>, // Slettes når los er over på ny modell og bruker K9LosOppgaveStatusDto
    val pleietrengendeAktørId: String? = null, // Slettes om vi ikke trenger funksjonalitet på og reservere oppgaver på tvers av pleietrengende.
    val type: String, // Skall ikke vara nullable, null = ukjent
    val ytelse: String? = null,
    val sendtInn: Boolean? = null, // Slettes, erstattes med status UTFØRT
    val ferdigstiltAv: String? = null, // Slettes
    val mottattDato: LocalDateTime? = null, // TODO: Mottatt dato for journalposten?
    val status: Oppgavestatus? = Oppgavestatus.AAPEN
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