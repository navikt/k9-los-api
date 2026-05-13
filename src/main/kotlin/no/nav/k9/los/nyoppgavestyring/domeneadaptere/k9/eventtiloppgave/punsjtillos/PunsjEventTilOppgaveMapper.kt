package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.punsjtillos

import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.K9FeltIder
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventLagret
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.*

class PunsjEventTilOppgaveMapper {
    fun lagOppgaveDto(eventLagret: EventLagret.K9Punsj, forrigeOppgave: OppgaveV3?): NyOppgaveVersjonInnsending {
        if (eventLagret.fagsystem != Fagsystem.PUNSJ) {
            throw IllegalStateException()
        }
        val event = eventLagret.eventDto

        return NyOppgaveversjon(
            dto = OppgaveDto(
                eksternId = event.eksternId.toString(),
                eksternVersjon = event.eventTid.toString(),
                område = "K9",
                kildeområde = "K9",
                type = "k9punsj",
                status = utledOppgavestatus(event).kode,
                endretTidspunkt = event.eventTid,
                reservasjonsnøkkel = utledReservasjonsnøkkel(eventLagret),
                feltverdier = lagFeltverdier(event, forrigeOppgave)
            )
        )
    }

    companion object {
        fun lagOppgaveDto(event: K9PunsjEventDto, forrigeOppgave: OppgaveV3?): OppgaveDto {
            return OppgaveDto(
                eksternId = event.eksternId.toString(),
                eksternVersjon = event.eventTid.toString(),
                område = "K9",
                kildeområde = "K9",
                type = "k9punsj",
                status = utledOppgavestatus(event).kode,
                endretTidspunkt = event.eventTid,
                reservasjonsnøkkel = utledReservasjonsnøkkel(event),
                feltverdier = lagFeltverdier(event, forrigeOppgave)
            )
        }

        fun utledOppgavestatus(event: K9PunsjEventDto): Oppgavestatus {
            return if (event.sendtInn == true || event.status == Oppgavestatus.LUKKET || event.aksjonspunktKoderMedStatusListe.isEmpty()) {
                Oppgavestatus.LUKKET
            } else if (oppgaveSkalHaVentestatus(event)) {
                Oppgavestatus.VENTER
            } else {
                Oppgavestatus.AAPEN
            }
        }

        fun utledReservasjonsnøkkel(eventLagret: EventLagret.K9Punsj): String {
            return utledReservasjonsnøkkel(eventLagret.eventDto)
        }

        fun utledReservasjonsnøkkel(eventDto: K9PunsjEventDto): String {
            return "K9_p_${eventDto.eksternId}"
        }


        private fun oppgaveSkalHaVentestatus(event: K9PunsjEventDto): Boolean {
            return event.aksjonspunktKoderMedStatusListe.filter { entry -> entry.value == AksjonspunktStatus.OPPRETTET.kode }
                .containsKey("MER_INFORMASJON")
        }

        private fun lagFeltverdier(
            event: K9PunsjEventDto,
            forrigeOppgave: OppgaveV3?
        ): List<OppgaveFeltverdiDto> {
            val journalførtTidspunkt = forrigeOppgave?.hentVerdi(K9FeltIder.JOURNALFORT_TIDSPUNKT) ?: event.journalførtTidspunkt?.toString()

            return listOfNotNull(
                event.aktørId?.let {
                    OppgaveFeltverdiDto(
                        nøkkel = K9FeltIder.AKTOR_ID,
                        verdi = it.aktørId,
                    )
                },
                event.pleietrengendeAktørId?.let {
                    OppgaveFeltverdiDto(
                        nøkkel = K9FeltIder.PLEIETRENGENDE_AKTOR_ID,
                        verdi = it,
                    )
                },
                OppgaveFeltverdiDto(
                    nøkkel = K9FeltIder.BEHANDLING_TYPEKODE,
                    verdi = event.type ?: forrigeOppgave?.hentVerdi(K9FeltIder.BEHANDLING_TYPEKODE) ?: BehandlingType.UKJENT.kode,
                ),
                OppgaveFeltverdiDto(
                    nøkkel = K9FeltIder.YTELSESTYPE,
                    verdi = event.ytelse ?: forrigeOppgave?.hentVerdi(K9FeltIder.YTELSESTYPE) ?: FagsakYtelseType.UKJENT.kode,
                ),
                event.ferdigstiltAv?.let {
                    OppgaveFeltverdiDto(
                        nøkkel = K9FeltIder.ANSVARLIG_SAKSBEHANDLER,
                        verdi = it,
                    )
                },
                OppgaveFeltverdiDto(
                    nøkkel = K9FeltIder.JOURNALPOST_ID,
                    verdi = event.journalpostId.verdi.toString(),
                ),
                OppgaveFeltverdiDto(
                    nøkkel = K9FeltIder.JOURNALFORT_TIDSPUNKT,
                    verdi = journalførtTidspunkt,
                ),
                OppgaveFeltverdiDto(
                    nøkkel = K9FeltIder.JOURNALFORT,
                    verdi = (journalførtTidspunkt != null).toString(),
                ),
                OppgaveFeltverdiDto(
                    nøkkel = K9FeltIder.REGISTRERT_DATO,
                    verdi = forrigeOppgave?.hentVerdi(K9FeltIder.REGISTRERT_DATO) ?: event.eventTid.toString(),
                ),
                OppgaveFeltverdiDto(
                    nøkkel = K9FeltIder.MOTTATT_DATO,
                    verdi = forrigeOppgave?.hentVerdi(K9FeltIder.MOTTATT_DATO) ?: event.eventTid.toString(),
                ),
                OppgaveFeltverdiDto(
                    nøkkel = K9FeltIder.HELAUTOMATISK_BEHANDLET,
                    verdi = "false"
                )
            )
        }
    }
}
