package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak


import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHendelse
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjEventDto
import no.nav.k9.sak.typer.AktørId
import no.nav.k9.sak.typer.JournalpostId
import java.time.LocalDateTime
import java.util.*

data class PunsjEventDtoBuilder(
    var eksternId: UUID = UUID.randomUUID(),
    var aktørId: String = Random().nextInt(0, 9999999).toString(),
    var pleietrengendeAktørId: String = Random().nextInt(0, 9999999).toString(),
    var journalpostId: String = Random().nextInt(0, 9999999).toString(),
    var eventTid: LocalDateTime? = null,
    var eventHendelse: EventHendelse = EventHendelse.AKSJONSPUNKT_OPPRETTET,
    var ytelse: FagsakYtelseType? = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
    var type: BehandlingType? = BehandlingType.PAPIRSØKNAD,
    var aksjonspunkter: MutableMap<String, AksjonspunktStatus> = mutableMapOf(),
    var sendtInn: Boolean = true,
    var ferdigstiltAv: String? = null,
    private var teller: Long = 0
) {
    fun papirsøknad(): PunsjEventDtoBuilder {
        return this.copy(
            type = BehandlingType.PAPIRSØKNAD,
            aksjonspunkter = mutableMapOf(
                "PUNSJ" to AksjonspunktStatus.OPPRETTET
            ),
            ferdigstiltAv = null,
        )
    }

    fun påVent(): PunsjEventDtoBuilder {
        return this.copy(
            aksjonspunkter = mutableMapOf(
                "PUNSJ" to AksjonspunktStatus.UTFØRT,
                "MER_INFORMASJON" to AksjonspunktStatus.OPPRETTET,
            ),
            ytelse = null,  // Ytelse settes til null ved "MER_INFORMASJON" på eventer fra testmiljø
            ferdigstiltAv = null,
            sendtInn = true,
        )
    }

    fun sendtInn(ansvarligBeslutter: Saksbehandler? = TestSaksbehandler.BIRGER_BESLUTTER): PunsjEventDtoBuilder {
        return this.copy(
            aksjonspunkter = mutableMapOf(),
            ferdigstiltAv = ansvarligBeslutter?.brukerIdent,
            sendtInn = true,
        )
    }

    fun medEksternId(eksternId: UUID): PunsjEventDtoBuilder {
        this.eksternId = eksternId
        return this
    }


    fun build(): PunsjEventDto {
        return PunsjEventDto(
            eksternId = eksternId,
            journalpostId = JournalpostId(journalpostId),
            eventTid = eventTid ?: LocalDateTime.now().plusSeconds(teller++),
            aktørId = AktørId(aktørId),
            pleietrengendeAktørId = pleietrengendeAktørId,
            aksjonspunktKoderMedStatusListe = aksjonspunkter.entries.associate { (aksjonspunkt, status) -> aksjonspunkt to status.kode }.toMutableMap(),
            type = type?.kode,
            ytelse = ytelse?.kode,
            sendtInn = sendtInn,
            ferdigstiltAv = ferdigstiltAv,
        )
    }
}