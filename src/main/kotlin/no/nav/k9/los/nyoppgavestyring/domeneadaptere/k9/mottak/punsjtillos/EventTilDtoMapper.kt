package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.punsjtillos

import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.integrasjon.kafka.dto.PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.*

class EventTilDtoMapper(
    private val oppgaveFeltVerdiUtledere: OppgaveFeltVerdiUtledere
) {

    fun lagOppgaveDto(event: PunsjEventDto, forrigeOppgave: OppgaveV3?): OppgaveDto {
        val oppgavestatus = when {
            event.sendtInn == true || event.status == Oppgavestatus.LUKKET || event.aksjonspunktKoderMedStatusListe.isEmpty() -> Oppgavestatus.LUKKET
            oppgaveSkalHaVentestatus(event) -> Oppgavestatus.VENTER
            else -> Oppgavestatus.AAPEN
        }
        return OppgaveDto(
            id = event.eksternId.toString(),
            versjon = event.eventTid.toString(),
            område = "K9",
            kildeområde = "K9",
            type = "k9punsj",
            status = oppgavestatus.kode,
            endretTidspunkt = event.eventTid,
            reservasjonsnøkkel = utledReservasjonsnøkkel(event),
            feltverdier = lagFeltverdier(event, oppgavestatus, forrigeOppgave)
        )
    }

    private fun utledReservasjonsnøkkel(event: PunsjEventDto): String {
        return "K9_p_${event.eksternId}"
    }


    private fun oppgaveSkalHaVentestatus(event: PunsjEventDto): Boolean {
        return event.aksjonspunktKoderMedStatusListe.filter { entry -> entry.value == AksjonspunktStatus.OPPRETTET.kode }
            .containsKey("MER_INFORMASJON")
    }

    private fun lagFeltverdier(
        event: PunsjEventDto,
        oppgavestatus: Oppgavestatus,
        forrigeOppgave: OppgaveV3?
    ): List<OppgaveFeltverdiDto> {
        val journalførtTidspunkt =
            forrigeOppgave?.hentVerdi("journalfortTidspunkt") ?: event.journalførtTidspunkt?.toString()

        return listOfNotNull(
            event.aktørId?.let {
                OppgaveFeltverdiDto(
                    nøkkel = "aktorId",
                    verdi = it.aktørId,
                )
            },
            event.pleietrengendeAktørId?.let {
                OppgaveFeltverdiDto(
                    nøkkel = "pleietrengendeAktorId",
                    verdi = it,
                )
            },
            OppgaveFeltverdiDto(
                nøkkel = "behandlingTypekode",
                verdi = event.type ?: forrigeOppgave?.hentVerdi("behandlingTypekode") ?: BehandlingType.UKJENT.kode,
            ),
            OppgaveFeltverdiDto(
                nøkkel = "ytelsestype",
                verdi = event.ytelse ?: forrigeOppgave?.hentVerdi("ytelsestype") ?: FagsakYtelseType.UKJENT.kode,
            ),
            event.ferdigstiltAv?.let {
                OppgaveFeltverdiDto(
                    nøkkel = "ansvarligSaksbehandler",
                    verdi = it,
                )
            },
            OppgaveFeltverdiDto(
                nøkkel = "journalpostId",
                verdi = event.journalpostId.verdi.toString(),
            ),
            OppgaveFeltverdiDto(
                nøkkel = "journalfortTidspunkt",
                verdi = journalførtTidspunkt,
            ),
            OppgaveFeltverdiDto(
                nøkkel = "journalfort",
                verdi = (journalførtTidspunkt != null).toString(),
            ),
            OppgaveFeltverdiDto(
                nøkkel = "registrertDato",
                verdi = forrigeOppgave?.hentVerdi("registrertDato") ?: event.eventTid.toString(),
            ),
            OppgaveFeltverdiDto(
                nøkkel = "mottattDato",
                verdi = forrigeOppgave?.hentVerdi("mottattDato") ?: event.eventTid.toString(),
            ),
            oppgaveFeltVerdiUtledere.utledFerdigstiltTidspunkt(oppgavestatus, forrigeOppgave, event.eventTid),
            oppgaveFeltVerdiUtledere.utledFerdigstiltEnhet(oppgavestatus, forrigeOppgave, event.ferdigstiltAv),
        )
    }
}
