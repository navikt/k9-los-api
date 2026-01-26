package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.tilbaketillos

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHendelse
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventLagret
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.AksjonspunktDefinisjonK9Tilbake
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventDto
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.kodeverk.AksjonspunktStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.AksjonspunktStatus.OPPRETTET
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.*
import org.jetbrains.annotations.VisibleForTesting
import java.time.temporal.ChronoUnit

class TilbakeEventTilOppgaveMapper {
    fun lagOppgaveDto(
        eventLagret: EventLagret.K9Tilbake,
        forrigeOppgave: OppgaveV3?,
        eventnummer: Int
    ): NyOppgaveVersjonInnsending {
        if (eventLagret.fagsystem != Fagsystem.K9TILBAKE) {
            throw IllegalArgumentException("Fagsystem er ikke TILBAKE")
        }
        val event = eventLagret.eventDto
        val oppgaveDto = OppgaveDto(
            eksternId = event.eksternId.toString(),
            eksternVersjon = event.eventTid.toString(),
            område = "K9",
            kildeområde = "K9",
            type = "k9tilbake",
            status = utledOppgavestatus(event).kode,
            endretTidspunkt = event.eventTid,
            reservasjonsnøkkel = utledReservasjonsnøkkel(event, erTilBeslutter(event)),
            feltverdier = lagFeltverdier(event, forrigeOppgave)
        )

        if (event.eventHendelse == EventHendelse.VASKEEVENT) {
            return VaskOppgaveversjon(
                dto = oppgaveDto,
                eventNummer = eventnummer
            )
        } else {
            return NyOppgaveversjon(oppgaveDto)
        }
    }

    companion object {
        fun lagOppgaveDto(event: K9TilbakeEventDto, forrigeOppgave: OppgaveV3?) =
            OppgaveDto(
                eksternId = event.eksternId.toString(),
                eksternVersjon = event.eventTid.toString(),
                område = "K9",
                kildeområde = "K9",
                type = "k9tilbake",
                status = utledOppgavestatus(event).kode,
                endretTidspunkt = event.eventTid,
                reservasjonsnøkkel = utledReservasjonsnøkkel(event, erTilBeslutter(event)),
                feltverdier = lagFeltverdier(event, forrigeOppgave)
            )

        fun utledReservasjonsnøkkel(eventLagret: EventLagret.K9Tilbake, erTilBeslutter: Boolean): String {
            return utledReservasjonsnøkkel(eventLagret.eventDto, erTilBeslutter)
        }

        fun utledReservasjonsnøkkel(event: K9TilbakeEventDto, erTilBeslutter: Boolean): String {
            return lagNøkkelAktør(event, erTilBeslutter)
        }

        @VisibleForTesting
        fun utledOppgavestatus(event: K9TilbakeEventDto): Oppgavestatus {
            return when (BehandlingStatus.fraKode(event.behandlingStatus)) {
                BehandlingStatus.OPPRETTET -> Oppgavestatus.UAVKLART
                BehandlingStatus.AVSLUTTET -> Oppgavestatus.LUKKET
                BehandlingStatus.FATTER_VEDTAK,
                BehandlingStatus.IVERKSETTER_VEDTAK,
                BehandlingStatus.UTREDES -> {
                    val harÅpentAutopunkt =
                        event.aksjonspunktKoderMedStatusListe.any {
                            it.value == OPPRETTET.kode && AksjonspunktDefinisjonK9Tilbake.fraKode(
                                it.key
                            ).erAutopunkt
                        }
                    val harÅpentAksjonspunkt =
                        event.aksjonspunktKoderMedStatusListe.any {
                            it.value == OPPRETTET.kode && !AksjonspunktDefinisjonK9Tilbake.fraKode(
                                it.key
                            ).erAutopunkt
                        }

                    if (harÅpentAutopunkt) {
                        Oppgavestatus.VENTER
                    } else if (harÅpentAksjonspunkt) {
                        Oppgavestatus.AAPEN
                    } else {
                        Oppgavestatus.UAVKLART
                    }
                }

                BehandlingStatus.SATT_PÅ_VENT,
                BehandlingStatus.LUKKET,
                BehandlingStatus.SENDT_INN -> throw IllegalStateException("Punsj-statuser ikke lov på tilbakekravsbehandling")
            }
        }

        private fun lagNøkkelAktør(event: K9TilbakeEventDto, tilBeslutter: Boolean): String {
            return if (tilBeslutter) {
                "K9_t_${event.ytelseTypeKode}_${event.aktørId}_beslutter"
            } else {
                "K9_t_${event.ytelseTypeKode}_${event.aktørId}"
            }
        }

        private fun erTilBeslutter(event: K9TilbakeEventDto): Boolean {
            return event.aksjonspunktKoderMedStatusListe.any {
                it.value == OPPRETTET.kode && AksjonspunktDefinisjonK9Tilbake.fraKode(it.key) == AksjonspunktDefinisjonK9Tilbake.FATTE_VEDTAK
            } && event.aksjonspunktKoderMedStatusListe.none {
                it.value == OPPRETTET.kode && AksjonspunktDefinisjonK9Tilbake.fraKode(it.key) == AksjonspunktDefinisjonK9Tilbake.FORESLÅ_VEDTAK
            }

        }

        private fun lagFeltverdier(
            event: K9TilbakeEventDto,
            forrigeOppgave: OppgaveV3?
        ): List<OppgaveFeltverdiDto> {
            val oppgaveFeltverdiDtos = mapEnkeltverdier(event, forrigeOppgave)

            utledAksjonspunkter(event, oppgaveFeltverdiDtos)
            utledAutomatiskBehandletFlagg(event, forrigeOppgave, oppgaveFeltverdiDtos)
            return oppgaveFeltverdiDtos
        }

        private fun mapEnkeltverdier(
            event: K9TilbakeEventDto,
            forrigeOppgave: OppgaveV3?
        ) = mutableListOf(
            OppgaveFeltverdiDto(
                nøkkel = "liggerHosBeslutter",
                verdi = erTilBeslutter(event).toString()
            ),
            OppgaveFeltverdiDto(
                nøkkel = "behandlingUuid",
                verdi = event.eksternId.toString()
            ),
            OppgaveFeltverdiDto(
                nøkkel = "aktorId",
                verdi = event.aktørId
            ),
            OppgaveFeltverdiDto(
                nøkkel = "fagsystem",
                verdi = event.fagsystem
            ),
            OppgaveFeltverdiDto(
                nøkkel = "saksnummer",
                verdi = event.saksnummer
            ),
            OppgaveFeltverdiDto(
                nøkkel = "resultattype",
                verdi = event.resultatType ?: BehandlingResultatType.IKKE_FASTSATT.kode
            ),
            OppgaveFeltverdiDto(
                nøkkel = "ytelsestype",
                verdi = event.ytelseTypeKode
            ),
            OppgaveFeltverdiDto(
                nøkkel = "behandlingsstatus",
                verdi = event.behandlingStatus ?: BehandlingStatus.UTREDES.kode
            ),
            OppgaveFeltverdiDto(
                nøkkel = "behandlingssteg",
                verdi = event.behandlingSteg
            ),
            OppgaveFeltverdiDto(
                nøkkel = "behandlingTypekode",
                verdi = event.behandlingTypeKode
            ),

            OppgaveFeltverdiDto(
                nøkkel = "ansvarligBeslutter",
                verdi = event.ansvarligBeslutterIdent ?: forrigeOppgave?.hentVerdi("ansvarligBeslutter")
            ),
            OppgaveFeltverdiDto(
                nøkkel = "ansvarligSaksbehandler",
                verdi = event.ansvarligSaksbehandlerIdent ?: forrigeOppgave?.hentVerdi("ansvarligSaksbehandler")
            ),
            OppgaveFeltverdiDto(
                nøkkel = "registrertDato",
                verdi = forrigeOppgave?.hentVerdi("registrertDato")
                    ?: event.opprettetBehandling.truncatedTo(ChronoUnit.SECONDS).toString()
            ),
            OppgaveFeltverdiDto(
                nøkkel = "mottattDato",
                verdi = forrigeOppgave?.hentVerdi("mottattDato")
                    ?: event.opprettetBehandling.truncatedTo(ChronoUnit.SECONDS).toString()
            ),
            OppgaveFeltverdiDto(
                nøkkel = "feilutbetaltBeløp",
                verdi = event.feilutbetaltBeløp?.toString() ?: forrigeOppgave?.hentVerdi("feilutbetaltBeløp")
            ),
            OppgaveFeltverdiDto(
                nøkkel = "førsteFeilutbetalingDato",
                verdi = event.førsteFeilutbetaling ?: forrigeOppgave?.hentVerdi("førsteFeilutbetalingDato")
            ),
            utledTidFørsteGangHosBeslutter(forrigeOppgave, event),
        ).filterNotNull().toMutableList()

        @VisibleForTesting
        fun utledTidFørsteGangHosBeslutter(
            forrigeOppgave: OppgaveV3?,
            event: K9TilbakeEventDto
        ) = forrigeOppgave?.hentVerdi("tidFørsteGangHosBeslutter")?.let {
            OppgaveFeltverdiDto(
                nøkkel = "tidFørsteGangHosBeslutter",
                verdi = forrigeOppgave.hentVerdi("tidFørsteGangHosBeslutter")
            )
        } ?: if (erTilBeslutter(event)) {
            OppgaveFeltverdiDto(
                nøkkel = "tidFørsteGangHosBeslutter",
                verdi = event.eventTid.toString()
            )
        } else {
            null
        }

        private fun utledAutomatiskBehandletFlagg(
            event: K9TilbakeEventDto,
            forrigeOppgave: OppgaveV3?,
            oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>
        ) {
            val harUtførtManueltAksjonspunkt = event.aksjonspunktKoderMedStatusListe
                .filter { it.value == AksjonspunktStatus.UTFØRT.kode }
                .map { AksjonspunktDefinisjonK9Tilbake.fraKode(it.key) }.any { !it.erAutopunkt }

            oppgaveFeltverdiDtos.add(
                OppgaveFeltverdiDto(
                    nøkkel = "helautomatiskBehandlet",
                    verdi = if (harUtførtManueltAksjonspunkt) false.toString() else true.toString()
                )
            )
        }

        private fun utledAksjonspunkter(
            event: K9TilbakeEventDto,
            oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>
        ) {
            val løsbareAksjonspunkter = event.aksjonspunktKoderMedStatusListe
                .filter { it.value == OPPRETTET.kode }
                .map { AksjonspunktDefinisjonK9Tilbake.fraKode(it.key) }
                .filter { !it.erAutopunkt }

            if (løsbareAksjonspunkter.isNotEmpty()) {
                oppgaveFeltverdiDtos.addAll(løsbareAksjonspunkter.map {
                    OppgaveFeltverdiDto(
                        nøkkel = "løsbartAksjonspunkt",
                        verdi = it.kode
                    )
                })
            } else {
                oppgaveFeltverdiDtos.add(
                    OppgaveFeltverdiDto(
                        nøkkel = "løsbartAksjonspunkt",
                        verdi = null
                    )
                )
            }
        }
    }
}
