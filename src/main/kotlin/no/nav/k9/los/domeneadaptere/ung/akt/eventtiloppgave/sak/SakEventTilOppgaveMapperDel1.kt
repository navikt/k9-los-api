package no.nav.k9.los.domeneadaptere.ung.akt.eventtiloppgave.sak

import no.nav.k9.los.domeneadaptere.eventlager.EventLagret
import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.domeneadaptere.ung.akt.kodeverk.AktivitetspengerOppgavetypenavn
import no.nav.k9.los.domeneadaptere.ung.akt.oppgavedefinisjon.AktivitetspengerFeltIder
import no.nav.k9.los.domeneadaptere.ung.eventmottak.ungsak.UngSakEventDto
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavemottak.*
import no.nav.ung.kodeverk.behandling.BehandlingResultatType
import no.nav.ung.kodeverk.behandling.BehandlingStatus
import no.nav.ung.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.ung.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.ung.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.ung.kodeverk.hendelse.EventHendelse
import no.nav.ung.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import java.time.temporal.ChronoUnit

class SakEventTilOppgaveMapperDel1 {
    fun lagOppgaveDto(eventLagret: EventLagret.UngSak, forrigeOppgave: OppgaveV3?, eventnummer: Int): NyOppgaveVersjonInnsending {
        if (eventLagret.fagsystem != Fagsystem.UNGSAK) {
            throw IllegalArgumentException("Kan kun mappe UNGSAK event til oppgave")
        }
        //TODO: guard for å skille del1/del2 i tillegg til dispatcher?
        val event = eventLagret.eventDto
        val oppgaveDto = OppgaveDto(
            eksternId = event.eksternId.toString(),
            eksternVersjon = event.eventTid.toString(),
            type = AktivitetspengerOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1,
            status = utledOppgavestatus(event),
            endretTidspunkt = event.eventTid,
            reservasjonsnøkkel = utledReservasjonsnokkel(event, erTilBeslutter(event)),
            feltverdier = lagFeltverdier(event, forrigeOppgave),
        )

        return if (erVaskeevent(eventLagret)) {
            VaskOppgaveversjon(dto = oppgaveDto, eventNummer = eventnummer)
        } else {
            NyOppgaveversjon(oppgaveDto)
        }
    }

    fun erVaskeevent(eventLagret: EventLagret.UngSak): Boolean {
        return eventLagret.eventDto.eventHendelse == EventHendelse.VASKEEVENT
    }

    companion object {
        fun utledOppgavestatus(event: UngSakEventDto): Oppgavestatus {
            return when (BehandlingStatus.fraKode(event.behandlingStatus)) {
                BehandlingStatus.OPPRETTET -> Oppgavestatus.UAVKLART
                BehandlingStatus.AVSLUTTET -> Oppgavestatus.LUKKET
                BehandlingStatus.FATTER_VEDTAK,
                BehandlingStatus.IVERKSETTER_VEDTAK,
                BehandlingStatus.UTREDES -> {
                    val harÅpentManueltAksjonspunkt: Boolean =
                        event.aksjonspunktTilstander
                            .filter { !AksjonspunktDefinisjon.fraKode(it.aksjonspunktKode()).erAutopunkt() }
                            .any { it.status == AksjonspunktStatus.OPPRETTET }
                    val harÅpentAutopunkt: Boolean =
                        event.aksjonspunktTilstander
                            .filter { AksjonspunktDefinisjon.fraKode(it.aksjonspunktKode()).erAutopunkt() }
                            .any { it.status == AksjonspunktStatus.OPPRETTET }
                    if (harÅpentAutopunkt) {
                        Oppgavestatus.VENTER
                    } else if (harÅpentManueltAksjonspunkt) {
                        Oppgavestatus.AAPEN
                    } else {
                        Oppgavestatus.UAVKLART
                    }
                }

                else -> Oppgavestatus.UAVKLART
            }
        }

        private fun utledReservasjonsnokkel(event: UngSakEventDto, tilBeslutter: Boolean): String {
            return if (tilBeslutter) {
                "UNG_b_${event.ytelseTypeKode}_${event.aktørId}_beslutter"
            } else {
                "UNG_b_${event.ytelseTypeKode}_${event.aktørId}"
            }
        }

        private fun erTilBeslutter(event: UngSakEventDto): Boolean {
                        return getåpneAksjonspunkter(event).firstOrNull { ap ->
                ap.aksjonspunktKode.equals(AksjonspunktDefinisjon.FATTER_VEDTAK.kode)
            } != null
        }

        private fun utledTidFørsteGangHosBeslutter(event: UngSakEventDto, forrigeOppgave: OppgaveV3?): OppgaveFeltverdiDto? {
            return forrigeOppgave?.hentVerdi(AktivitetspengerFeltIder.Saksbehandling.TID_FORSTE_GANG_HOS_BESLUTTER)?.let {
                OppgaveFeltverdiDto(
                    nøkkel = AktivitetspengerFeltIder.Saksbehandling.TID_FORSTE_GANG_HOS_BESLUTTER,
                    verdi = it,
                )
            } ?: if (erTilBeslutter(event)) {
                OppgaveFeltverdiDto(
                    nøkkel = AktivitetspengerFeltIder.Saksbehandling.TID_FORSTE_GANG_HOS_BESLUTTER,
                    verdi = event.eventTid.toString(),
                )
            } else {
                null
            }
        }

        private fun getåpneAksjonspunkter(event: UngSakEventDto) =
            event.aksjonspunktTilstander.filter { aksjonspunktTilstand ->
                aksjonspunktTilstand.status.erÅpentAksjonspunkt()
            }

        private fun lagFeltverdier(
            event: UngSakEventDto,
            forrigeOppgave: OppgaveV3?,
        ): List<OppgaveFeltverdiDto> {
            val feltverdier = mapEnkeltverdier(event, forrigeOppgave)
            utledAksjonspunkter(event, feltverdier)
            utledApneAksjonspunkter(event, feltverdier)
            utledFremtidigeAksjonspunkter(event, feltverdier)
            utledUtforteAksjonspunkter(event, feltverdier)
            utledAvbrutteAksjonspunkter(event, feltverdier)
            utledTotrinnskontroll(event, feltverdier)
            utledAktivVentetid(event, feltverdier)
            utledBehandlingsarsaker(event, feltverdier)
            return feltverdier
        }

        private fun getApneAksjonspunkter(event: UngSakEventDto): List<AksjonspunktTilstandDto> {
            return event.aksjonspunktTilstander.filter { aksjonspunktTilstand ->
                aksjonspunktTilstand.status.erÅpentAksjonspunkt()
            }
        }

        private fun mapEnkeltverdier(event: UngSakEventDto, forrigeOppgave: OppgaveV3?): MutableList<OppgaveFeltverdiDto> {
            return mutableListOf(
                OppgaveFeltverdiDto(
                    nøkkel = AktivitetspengerFeltIder.Saksbehandling.LIGGER_HOS_BESLUTTER,
                    verdi = erTilBeslutter(event).toString(),
                ),
                OppgaveFeltverdiDto(
                    nøkkel = AktivitetspengerFeltIder.Behandling.UUID,
                    verdi = event.eksternId.toString(),
                ),
                OppgaveFeltverdiDto(
                    nøkkel = AktivitetspengerFeltIder.Sak.AKTOR_ID,
                    verdi = event.aktørId,
                ),
                OppgaveFeltverdiDto(
                    nøkkel = AktivitetspengerFeltIder.Sak.FAGSYSTEM,
                    verdi = event.fagsystem.kode,
                ),
                OppgaveFeltverdiDto(
                    nøkkel = AktivitetspengerFeltIder.Sak.SAKSNUMMER,
                    verdi = event.saksnummer,
                ),
                OppgaveFeltverdiDto(
                    nøkkel = AktivitetspengerFeltIder.Behandling.RESULTATTYPE,
                    verdi = event.resultatType ?: BehandlingResultatType.IKKE_FASTSATT.kode,
                ),
                OppgaveFeltverdiDto(
                    nøkkel = AktivitetspengerFeltIder.Vedtak.YTELSESTYPE,
                    verdi = event.ytelseTypeKode,
                ),
                OppgaveFeltverdiDto(
                    nøkkel = AktivitetspengerFeltIder.Behandling.STATUS,
                    verdi = event.behandlingStatus,
                ),
                OppgaveFeltverdiDto(
                    nøkkel = AktivitetspengerFeltIder.Behandling.STEG,
                    verdi = event.behandlingSteg,
                ),
                OppgaveFeltverdiDto(
                    nøkkel = AktivitetspengerFeltIder.Vedtak.BEHANDLENDE_ENHET,
                    verdi = event.behandlendeEnhet,
                ),
                OppgaveFeltverdiDto(
                    nøkkel = AktivitetspengerFeltIder.Behandling.TYPEKODE,
                    verdi = event.behandlingTypeKode,
                ),
                OppgaveFeltverdiDto(
                    nøkkel = AktivitetspengerFeltIder.Saksbehandling.ANSVARLIG_BESLUTTER,
                    verdi = event.ansvarligBeslutterForTotrinn ?: forrigeOppgave?.hentVerdi(AktivitetspengerFeltIder.Saksbehandling.ANSVARLIG_BESLUTTER),
                ),
                OppgaveFeltverdiDto(
                    nøkkel = AktivitetspengerFeltIder.Saksbehandling.ANSVARLIG_SAKSBEHANDLER,
                    verdi = event.ansvarligSaksbehandlerForTotrinn
                        ?: event.navKontorAnsvarligSaksbehandler
                        ?: forrigeOppgave?.hentVerdi(AktivitetspengerFeltIder.Saksbehandling.ANSVARLIG_SAKSBEHANDLER),
                ),
                OppgaveFeltverdiDto(
                    nøkkel = AktivitetspengerFeltIder.Sak.MOTTATT_DATO,
                    verdi = event.eldsteDatoMedEndringFraSøker?.truncatedTo(ChronoUnit.SECONDS)?.toString()
                        ?: forrigeOppgave?.hentVerdi(AktivitetspengerFeltIder.Sak.MOTTATT_DATO)
                        ?: forrigeOppgave?.hentVerdi(AktivitetspengerFeltIder.Sak.REGISTRERT_DATO)
                        ?: event.opprettetBehandling.truncatedTo(ChronoUnit.SECONDS).toString(),
                ),
                OppgaveFeltverdiDto(
                    nøkkel = AktivitetspengerFeltIder.Sak.REGISTRERT_DATO,
                    verdi = forrigeOppgave?.hentVerdi(AktivitetspengerFeltIder.Sak.REGISTRERT_DATO)
                        ?: event.opprettetBehandling.truncatedTo(ChronoUnit.SECONDS).toString(),
                ),
                OppgaveFeltverdiDto(
                    nøkkel = AktivitetspengerFeltIder.Vedtak.DATO,
                    verdi = event.vedtaksdato?.toString() ?: forrigeOppgave?.hentVerdi(AktivitetspengerFeltIder.Vedtak.DATO),
                ),
                event.nyeKrav?.let {
                    OppgaveFeltverdiDto(
                        nøkkel = AktivitetspengerFeltIder.Soknad.NYE_KRAV,
                        verdi = it.toString(),
                    )
                },
                utledTidFørsteGangHosBeslutter(event, forrigeOppgave)
            ).filterNotNull().toMutableList()
        }

        private fun utledBehandlingsarsaker(event: UngSakEventDto, feltverdier: MutableList<OppgaveFeltverdiDto>) {
            if (event.behandlingsårsaker.isNotEmpty()) {
                feltverdier.addAll(event.behandlingsårsaker.map { arsak ->
                    OppgaveFeltverdiDto(
                        nøkkel = AktivitetspengerFeltIder.Behandling.ARSAK,
                        verdi = arsak,
                    )
                })
            } else {
                feltverdier.add(
                    OppgaveFeltverdiDto(
                        nøkkel = AktivitetspengerFeltIder.Behandling.ARSAK,
                        verdi = null,
                    )
                )
            }
        }

        private fun utledAksjonspunkter(event: UngSakEventDto, feltverdier: MutableList<OppgaveFeltverdiDto>) {
            if (event.aksjonspunktTilstander.isNotEmpty()) {
                feltverdier.addAll(event.aksjonspunktTilstander.map { aksjonspunkt ->
                    OppgaveFeltverdiDto(
                        nøkkel = AktivitetspengerFeltIder.Aksjonspunkt.ALLE,
                        verdi = aksjonspunkt.aksjonspunktKode
                    )
                })
            } else {
                feltverdier.add(
                    OppgaveFeltverdiDto(
                        nøkkel = AktivitetspengerFeltIder.Aksjonspunkt.ALLE,
                        verdi = null,
                    )
                )
            }
        }

        private fun utledApneAksjonspunkter(event: UngSakEventDto, feltverdier: MutableList<OppgaveFeltverdiDto>) {
            val åpneAksjonspunkter = getApneAksjonspunkter(event)
            if (åpneAksjonspunkter.isNotEmpty()) {
                åpneAksjonspunkter.forEach { åpentAksjonspunkt ->
                    feltverdier.add(
                        OppgaveFeltverdiDto(
                            nøkkel = AktivitetspengerFeltIder.Aksjonspunkt.AKTIVE,
                            verdi = åpentAksjonspunkt.aksjonspunktKode
                        )
                    )
                }
                val løsbartAksjonspunkt = if (event.behandlingSteg != null) {
                    åpneAksjonspunkter.firstOrNull { åpentAksjonspunkt ->
                        val aksjonspunktDefinisjon = AksjonspunktDefinisjon.fraKode(åpentAksjonspunkt.aksjonspunktKode)
                        !aksjonspunktDefinisjon.erAutopunkt() && aksjonspunktDefinisjon.behandlingSteg != null && aksjonspunktDefinisjon.behandlingSteg.kode == event.behandlingSteg
                    }?.aksjonspunktKode
                } else {
                    null
                }
                feltverdier.add(
                    OppgaveFeltverdiDto(
                        nøkkel = AktivitetspengerFeltIder.Aksjonspunkt.LOSBART,
                        verdi = løsbartAksjonspunkt,
                    )
                )
            } else {
                feltverdier.add(
                    OppgaveFeltverdiDto(
                        nøkkel = AktivitetspengerFeltIder.Aksjonspunkt.AKTIVE,
                        verdi = null
                    )
                )
            }
        }

        private fun utledFremtidigeAksjonspunkter(event: UngSakEventDto, feltverdier: MutableList<OppgaveFeltverdiDto>) {
            val fremtidige = getApneAksjonspunkter(event).filter { åpentAksjonspunkt ->
                val aksjonspunktDefinisjon = AksjonspunktDefinisjon.fraKode(åpentAksjonspunkt.aksjonspunktKode)
                !aksjonspunktDefinisjon.erAutopunkt() &&
                    (aksjonspunktDefinisjon.behandlingSteg == null || aksjonspunktDefinisjon.behandlingSteg.kode != event.behandlingSteg)
            }

            if (fremtidige.isNotEmpty()) {
                feltverdier.addAll(fremtidige.map { åpentAksjonspunkt ->
                    OppgaveFeltverdiDto(
                        nøkkel = AktivitetspengerFeltIder.Aksjonspunkt.FREMTIDIG,
                        verdi = åpentAksjonspunkt.aksjonspunktKode,
                    )
                })
            } else {
                feltverdier.add(
                    OppgaveFeltverdiDto(
                        nøkkel = AktivitetspengerFeltIder.Aksjonspunkt.FREMTIDIG,
                        verdi = null,
                    )
                )
            }
        }

        private fun utledUtforteAksjonspunkter(event: UngSakEventDto, feltverdier: MutableList<OppgaveFeltverdiDto>) {
            val utforte = event.aksjonspunktTilstander.filter { it.status == AksjonspunktStatus.UTFØRT }
            if (utforte.isNotEmpty()) {
                feltverdier.addAll(utforte.map { aksjonspunkt ->
                    OppgaveFeltverdiDto(
                        nøkkel = AktivitetspengerFeltIder.Aksjonspunkt.UTFORT,
                        verdi = aksjonspunkt.aksjonspunktKode,
                    )
                })
            } else {
                feltverdier.add(
                    OppgaveFeltverdiDto(
                        nøkkel = AktivitetspengerFeltIder.Aksjonspunkt.UTFORT,
                        verdi = null,
                    )
                )
            }
        }

        private fun utledAvbrutteAksjonspunkter(event: UngSakEventDto, feltverdier: MutableList<OppgaveFeltverdiDto>) {
            val avbrutte = event.aksjonspunktTilstander.filter { it.status == AksjonspunktStatus.AVBRUTT }
            if (avbrutte.isNotEmpty()) {
                feltverdier.addAll(avbrutte.map { aksjonspunktTilstand ->
                    OppgaveFeltverdiDto(
                        nøkkel = AktivitetspengerFeltIder.Aksjonspunkt.AVBRUTT,
                        verdi = aksjonspunktTilstand.aksjonspunktKode,
                    )
                })
            } else {
                feltverdier.add(
                    OppgaveFeltverdiDto(
                        nøkkel = AktivitetspengerFeltIder.Aksjonspunkt.AVBRUTT,
                        verdi = null,
                    )
                )
            }
        }

        private fun utledTotrinnskontroll(event: UngSakEventDto, feltverdier: MutableList<OppgaveFeltverdiDto>) {
            feltverdier.add(
                OppgaveFeltverdiDto(
                    nøkkel = AktivitetspengerFeltIder.Vedtak.TOTRINNSKONTROLL,
                    verdi = event.aksjonspunktTilstander.filter { aksjonspunktTilstandDto ->
                        aksjonspunktTilstandDto.aksjonspunktKode.equals("5015") && aksjonspunktTilstandDto.status !in listOf(
                            AksjonspunktStatus.AVBRUTT,
                        )
                    }.isNotEmpty().toString(),
                )
            )
        }

        private fun utledAktivVentetid(event: UngSakEventDto, feltverdier: MutableList<OppgaveFeltverdiDto>) {
            val åpneAksjonspunkter = getApneAksjonspunkter(event)
            val ventendeAksjonspunkt = åpneAksjonspunkter
                .filter { aksjonspunktTilstandDto ->
                    aksjonspunktTilstandDto.venteårsak != null && aksjonspunktTilstandDto.venteårsak != Venteårsak.UDEFINERT &&
                        aksjonspunktTilstandDto.status == AksjonspunktStatus.OPPRETTET
                }
                .singleOrNull()

            feltverdier.add(
                OppgaveFeltverdiDto(
                    nøkkel = AktivitetspengerFeltIder.Ventetid.AKTIV_ARSAK,
                    verdi = ventendeAksjonspunkt?.venteårsak?.kode?.toString(),
                )
            )
            feltverdier.add(
                OppgaveFeltverdiDto(
                    nøkkel = AktivitetspengerFeltIder.Ventetid.AKTIV_FRIST,
                    verdi = ventendeAksjonspunkt?.fristTid?.toString(),
                )
            )
        }
    }
}

