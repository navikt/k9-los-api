package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.tilbaketillos

import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.K9FeltIder
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHendelse
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventLagret
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.AksjonspunktDefinisjonK9Tilbake
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventDto
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
            utledUtførteAksjonspunkter(event, oppgaveFeltverdiDtos)
            utledAvbrutteAksjonspunkter(event, oppgaveFeltverdiDtos)
            utledAutomatiskBehandletFlagg(event, forrigeOppgave, oppgaveFeltverdiDtos)
            return oppgaveFeltverdiDtos
        }

        private fun mapEnkeltverdier(
            event: K9TilbakeEventDto,
            forrigeOppgave: OppgaveV3?
        ) = mutableListOf(
            OppgaveFeltverdiDto(
                nøkkel = K9FeltIder.LIGGER_HOS_BESLUTTER,
                verdi = erTilBeslutter(event).toString()
            ),
            OppgaveFeltverdiDto(
                nøkkel = K9FeltIder.BEHANDLING_UUID,
                verdi = event.eksternId.toString()
            ),
            OppgaveFeltverdiDto(
                nøkkel = K9FeltIder.AKTOR_ID,
                verdi = event.aktørId
            ),
            OppgaveFeltverdiDto(
                nøkkel = K9FeltIder.FAGSYSTEM,
                verdi = event.fagsystem
            ),
            OppgaveFeltverdiDto(
                nøkkel = K9FeltIder.SAKSNUMMER,
                verdi = event.saksnummer
            ),
            OppgaveFeltverdiDto(
                nøkkel = K9FeltIder.RESULTATTYPE,
                verdi = event.resultatType ?: BehandlingResultatType.IKKE_FASTSATT.kode
            ),
            OppgaveFeltverdiDto(
                nøkkel = K9FeltIder.YTELSESTYPE,
                verdi = event.ytelseTypeKode
            ),
            OppgaveFeltverdiDto(
                nøkkel = K9FeltIder.BEHANDLINGSSTATUS,
                verdi = event.behandlingStatus ?: BehandlingStatus.UTREDES.kode
            ),
            OppgaveFeltverdiDto(
                nøkkel = K9FeltIder.BEHANDLINGSSTEG,
                verdi = event.behandlingSteg
            ),
            OppgaveFeltverdiDto(
                nøkkel = K9FeltIder.BEHANDLING_TYPEKODE,
                verdi = event.behandlingTypeKode
            ),

            OppgaveFeltverdiDto(
                nøkkel = K9FeltIder.ANSVARLIG_BESLUTTER,
                verdi = event.ansvarligBeslutterIdent ?: forrigeOppgave?.hentVerdi(K9FeltIder.ANSVARLIG_BESLUTTER)
            ),
            OppgaveFeltverdiDto(
                nøkkel = K9FeltIder.ANSVARLIG_SAKSBEHANDLER,
                verdi = event.ansvarligSaksbehandlerIdent ?: forrigeOppgave?.hentVerdi(K9FeltIder.ANSVARLIG_SAKSBEHANDLER)
            ),
            OppgaveFeltverdiDto(
                nøkkel = K9FeltIder.REGISTRERT_DATO,
                verdi = forrigeOppgave?.hentVerdi(K9FeltIder.REGISTRERT_DATO)
                    ?: event.opprettetBehandling.truncatedTo(ChronoUnit.SECONDS).toString()
            ),
            OppgaveFeltverdiDto(
                nøkkel = K9FeltIder.MOTTATT_DATO,
                verdi = forrigeOppgave?.hentVerdi(K9FeltIder.MOTTATT_DATO)
                    ?: event.opprettetBehandling.truncatedTo(ChronoUnit.SECONDS).toString()
            ),
            OppgaveFeltverdiDto(
                nøkkel = K9FeltIder.FEILUTBETALT_BELOP,
                verdi = event.feilutbetaltBeløp?.toString() ?: forrigeOppgave?.hentVerdi(K9FeltIder.FEILUTBETALT_BELOP)
            ),
            OppgaveFeltverdiDto(
                nøkkel = K9FeltIder.FORSTE_FEILUTBETALING_DATO,
                verdi = event.førsteFeilutbetaling ?: forrigeOppgave?.hentVerdi(K9FeltIder.FORSTE_FEILUTBETALING_DATO)
            ),
            utledTidFørsteGangHosBeslutter(forrigeOppgave, event),
        ).filterNotNull().toMutableList()

        @VisibleForTesting
        fun utledTidFørsteGangHosBeslutter(
            forrigeOppgave: OppgaveV3?,
            event: K9TilbakeEventDto
        ) = forrigeOppgave?.hentVerdi(K9FeltIder.TID_FORSTE_GANG_HOS_BESLUTTER)?.let {
            OppgaveFeltverdiDto(
                nøkkel = K9FeltIder.TID_FORSTE_GANG_HOS_BESLUTTER,
                verdi = forrigeOppgave.hentVerdi(K9FeltIder.TID_FORSTE_GANG_HOS_BESLUTTER)
            )
        } ?: if (erTilBeslutter(event)) {
            OppgaveFeltverdiDto(
                nøkkel = K9FeltIder.TID_FORSTE_GANG_HOS_BESLUTTER,
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
                    nøkkel = K9FeltIder.HELAUTOMATISK_BEHANDLET,
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
                        nøkkel = K9FeltIder.LOSBART_AKSJONSPUNKT,
                        verdi = it.kode
                    )
                })
            } else {
                oppgaveFeltverdiDtos.add(
                    OppgaveFeltverdiDto(
                        nøkkel = K9FeltIder.LOSBART_AKSJONSPUNKT,
                        verdi = null
                    )
                )
            }
        }

        private fun utledUtførteAksjonspunkter(
            event: K9TilbakeEventDto,
            oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>
        ) {
            val utførte = event.aksjonspunktKoderMedStatusListe
                .filter { it.value == AksjonspunktStatus.UTFØRT.kode }

            if (utførte.isNotEmpty()) {
                oppgaveFeltverdiDtos.addAll(utførte.map {
                    OppgaveFeltverdiDto(
                        nøkkel = K9FeltIder.UTFORT_AKSJONSPUNKT,
                        verdi = it.key
                    )
                })
            } else {
                oppgaveFeltverdiDtos.add(
                    OppgaveFeltverdiDto(
                        nøkkel = K9FeltIder.UTFORT_AKSJONSPUNKT,
                        verdi = null
                    )
                )
            }
        }

        private fun utledAvbrutteAksjonspunkter(
            event: K9TilbakeEventDto,
            oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>
        ) {
            val avbrutte = event.aksjonspunktKoderMedStatusListe
                .filter { it.value == AksjonspunktStatus.AVBRUTT.kode }

            if (avbrutte.isNotEmpty()) {
                oppgaveFeltverdiDtos.addAll(avbrutte.map {
                    OppgaveFeltverdiDto(
                        nøkkel = K9FeltIder.AVBRUTT_AKSJONSPUNKT,
                        verdi = it.key
                    )
                })
            } else {
                oppgaveFeltverdiDtos.add(
                    OppgaveFeltverdiDto(
                        nøkkel = K9FeltIder.AVBRUTT_AKSJONSPUNKT,
                        verdi = null
                    )
                )
            }
        }
    }
}
