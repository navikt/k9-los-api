package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.punsjtillos

import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.integrasjon.kafka.dto.PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdiDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus

class EventTilDtoMapper {
    companion object {

        fun lagOppgaveDto(event: PunsjEventDto, forrigeOppgave: OppgaveV3?): OppgaveDto {
            return OppgaveDto(
                id = event.eksternId.toString(),
                versjon = event.eventTid.toString(),
                område = "K9",
                kildeområde = "K9",
                type = "k9punsj",
                status =
                if (event.sendtInn == true || event.status == Oppgavestatus.LUKKET || event.aksjonspunktKoderMedStatusListe.isEmpty()) {
                    Oppgavestatus.LUKKET.kode
                } else if (oppgaveSkalHaVentestatus(event)) {
                    Oppgavestatus.VENTER.kode
                } else {
                    Oppgavestatus.AAPEN.kode
                },
                endretTidspunkt = event.eventTid,
                reservasjonsnøkkel = utledReservasjonsnøkkel(event),
                feltverdier = lagFeltverdier(event, forrigeOppgave)
            )
        }

        fun utledReservasjonsnøkkel(event: PunsjEventDto): String {
            return "K9_p_${event.eksternId}"
        }


        private fun oppgaveSkalHaVentestatus(event: PunsjEventDto): Boolean {
            return event.aksjonspunktKoderMedStatusListe.filter { entry -> entry.value == AksjonspunktStatus.OPPRETTET.kode }
                .containsKey("MER_INFORMASJON")
        }

        private fun lagFeltverdier(
            event: PunsjEventDto,
            forrigeOppgave: OppgaveV3?
        ): List<OppgaveFeltverdiDto> {
            val journalførtTidspunkt = forrigeOppgave?.hentVerdi("journalfortTidspunkt") ?: event.journalførtTidspunkt?.toString()

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
                    verdi = (event.type?.let { BehandlingType.fraKode(it) } ?: BehandlingType.UKJENT).kode,
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "ytelsestype",
                    verdi = event.ytelse,
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
                )
            )
        }
    }
}
