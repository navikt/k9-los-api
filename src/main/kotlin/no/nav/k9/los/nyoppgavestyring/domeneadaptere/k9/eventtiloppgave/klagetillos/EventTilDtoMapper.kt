package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos

import no.nav.k9.klage.kodeverk.behandling.*
import no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktType
import no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.k9.klage.kontrakt.behandling.oppgavetillos.Aksjonspunkttilstand
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageEventDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdiDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import org.jetbrains.annotations.VisibleForTesting

class EventTilDtoMapper {

    companion object {
        const val KLAGE_PREFIX = "KLAGE"
        const val KLAGE_PREFIX_VISNING = "Klage - "

        private val MANUELLE_AKSJONSPUNKTER = AksjonspunktDefinisjon.values().filter { aksjonspunktDefinisjon ->
            aksjonspunktDefinisjon.aksjonspunktType == AksjonspunktType.MANUELL
        }.map { aksjonspunktDefinisjon -> aksjonspunktDefinisjon.kode }

        private val AUTOPUNKTER = AksjonspunktDefinisjon.values().filter { aksjonspunktDefinisjon ->
            aksjonspunktDefinisjon.aksjonspunktType == AksjonspunktType.AUTOPUNKT
        }.map { aksjonspunktDefinisjon -> aksjonspunktDefinisjon.kode }

        internal fun lagOppgaveDto(
            event: K9KlageEventDto,
            forrigeOppgave: OppgaveV3?
        ) = OppgaveDto(
            id = event.eksternId.toString(),
            versjon = event.eventTid.toString(),
            område = "K9",
            kildeområde = "K9",
            type = "k9klage",
            status = if (event.behandlingSteg == BehandlingStegType.OVERFØRT_NK.kode) {
                Oppgavestatus.LUKKET.kode
            } else {
                if (event.aksjonspunkttilstander.any { aksjonspunktTilstandDto -> aksjonspunktTilstandDto.status.erÅpentAksjonspunkt() }) {
                    if (oppgaveSkalHaVentestatus(event)) {
                        Oppgavestatus.VENTER.kode
                    } else {
                        Oppgavestatus.AAPEN.kode
                    }
                } else {
                    if (event.behandlingStatus == BehandlingStatus.UTREDES.toString()) {
                        Oppgavestatus.AAPEN.kode
                    } else {
                        Oppgavestatus.LUKKET.kode
                    }
                }
            },
            endretTidspunkt = event.eventTid,
            reservasjonsnøkkel = utledReservasjonsnøkkel(event),
            feltverdier = lagFeltverdier(event, forrigeOppgave)
        )

        private fun utledReservasjonsnøkkel(event: K9KlageEventDto): String {
            return if (erTilBeslutter(event)) {
                "K9_k_${event.ytelseTypeKode}_${event.aktørId}_beslutter"
            } else {
                "K9_k_${event.ytelseTypeKode}_${event.aktørId}"
            }
        }

        private fun oppgaveSkalHaVentestatus(event: K9KlageEventDto): Boolean {
            val oppgaveFeltverdiDtos = mutableListOf<OppgaveFeltverdiDto>()
            val åpneAksjonspunkter = getåpneAksjonspunkter(event)

            val harAutopunkt = åpneAksjonspunkter.any { aksjonspunktTilstandDto ->
                AUTOPUNKTER.contains(aksjonspunktTilstandDto.aksjonspunktKode)
            }

            val harManueltAksjonspunkt = event.aksjonspunkttilstander
                .filter { aksjonspunkttilstand -> aksjonspunkttilstand.status != AksjonspunktStatus.AVBRUTT }
                .any { aksjonspunktTilstandDto -> MANUELLE_AKSJONSPUNKTER.contains(aksjonspunktTilstandDto.aksjonspunktKode) }

            utledAvventerSaksbehandler(
                harManueltAksjonspunkt = harManueltAksjonspunkt,
                harAutopunkt = harAutopunkt,
                oppgaveFeltverdiDtos = oppgaveFeltverdiDtos
            )

            return oppgaveFeltverdiDtos.first().nøkkel == "false"
        }

        private fun erTilBeslutter(event: K9KlageEventDto): Boolean {
            return getåpneAksjonspunkter(event).firstOrNull { ap ->
                ap.aksjonspunktKode.equals(AksjonspunktDefinisjon.FATTER_VEDTAK.kode)
            } != null
        }

        private fun lagFeltverdier(
            event: K9KlageEventDto,
            forrigeOppgave: OppgaveV3?
        ): List<OppgaveFeltverdiDto> {
            val oppgaveFeltverdiDtos = mapEnkeltverdier(event, forrigeOppgave)

            utledPåklagdBehandlingtype(event, oppgaveFeltverdiDtos)

            val åpneAksjonspunkter = getåpneAksjonspunkter(event)

            val harAutopunkt = åpneAksjonspunkter.any { aksjonspunktTilstandDto ->
                AUTOPUNKTER.contains(aksjonspunktTilstandDto.aksjonspunktKode)
            }

            val harManueltAksjonspunkt = åpneAksjonspunkter.any { aksjonspunktTilstandDto ->
                MANUELLE_AKSJONSPUNKTER.contains(aksjonspunktTilstandDto.aksjonspunktKode)
            }

            utledAksjonspunkter(event, oppgaveFeltverdiDtos)
            utledÅpneAksjonspunkter(åpneAksjonspunkter, oppgaveFeltverdiDtos)
            utledLøsbartAksjonspunkt(event.behandlingSteg, åpneAksjonspunkter, oppgaveFeltverdiDtos)

            utledTidspunktOversendtKabal(event, oppgaveFeltverdiDtos)

            utledVenteÅrsakOgFrist(åpneAksjonspunkter, oppgaveFeltverdiDtos)
            //automatiskBehandletFlagg er defaultet til False p.t.
            utledAvventerSaksbehandler(harManueltAksjonspunkt, harAutopunkt, oppgaveFeltverdiDtos)
            utledBehandlingsårsaker(event, oppgaveFeltverdiDtos)

            return oppgaveFeltverdiDtos
        }

        private fun utledVenteÅrsakOgFrist(
            åpneAksjonspunkter: List<Aksjonspunkttilstand>,
            oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>
        ) {
            åpneAksjonspunkter.filter { aksjonspunktTilstandDto ->
                aksjonspunktTilstandDto.venteårsak != Venteårsak.UDEFINERT &&
                        aksjonspunktTilstandDto.venteårsak != null
            }.singleOrNull { aksjonspunktTilstandDto ->
                oppgaveFeltverdiDtos.add(
                    OppgaveFeltverdiDto(
                        nøkkel = "aktivVenteårsak",
                        verdi = aksjonspunktTilstandDto.venteårsak.kode.toString()
                    )
                )
                oppgaveFeltverdiDtos.add(
                    OppgaveFeltverdiDto(
                        nøkkel = "aktivVentefrist",
                        verdi = aksjonspunktTilstandDto.fristTid.toString()
                    )
                )
            }
        }

        private fun getåpneAksjonspunkter(event: K9KlageEventDto) =
            event.aksjonspunkttilstander.filter { aksjonspunkttilstand ->
                aksjonspunkttilstand.status.erÅpentAksjonspunkt()
            }

        private fun utledÅpneAksjonspunkter(
            åpneAksjonspunkter: List<Aksjonspunkttilstand>,
            oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>
        ) {
            if (åpneAksjonspunkter.isNotEmpty()) {
                åpneAksjonspunkter.map { åpentAksjonspunkt ->
                    oppgaveFeltverdiDtos.add(
                        OppgaveFeltverdiDto(
                            nøkkel = "aktivtAksjonspunkt",
                            verdi = KLAGE_PREFIX + åpentAksjonspunkt.aksjonspunktKode
                        )
                    )
                }
            } else {
                oppgaveFeltverdiDtos.add(
                    OppgaveFeltverdiDto(
                        nøkkel = "aktivtAksjonspunkt",
                        verdi = null
                    )
                )
            }
        }

        private fun utledLøsbartAksjonspunkt(
            behandlingSteg: String?,
            åpneAksjonspunkter: List<Aksjonspunkttilstand>,
            oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>
        ) {
            if (behandlingSteg != null) {
                åpneAksjonspunkter.firstOrNull { åpentAksjonspunkt ->
                    val aksjonspunktDefinisjon = AksjonspunktDefinisjon.fraKode(åpentAksjonspunkt.aksjonspunktKode)
                    !aksjonspunktDefinisjon.erAutopunkt() && aksjonspunktDefinisjon.behandlingSteg != null && aksjonspunktDefinisjon.behandlingSteg.kode == behandlingSteg
                }?.let {
                    oppgaveFeltverdiDtos.add(
                        OppgaveFeltverdiDto(
                            nøkkel = "løsbartAksjonspunkt",
                            verdi = KLAGE_PREFIX + it.aksjonspunktKode
                        )
                    )
                }
            }
        }

        private fun utledAksjonspunkter(
            event: K9KlageEventDto,
            oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>
        ) {
            if (event.aksjonspunkttilstander.isNotEmpty()) {
                oppgaveFeltverdiDtos.addAll(event.aksjonspunkttilstander.map { aksjonspunkttilstand ->
                    OppgaveFeltverdiDto(
                        nøkkel = "aksjonspunkt",
                        verdi = KLAGE_PREFIX + aksjonspunkttilstand.aksjonspunktKode
                    )
                })
            } else {
                oppgaveFeltverdiDtos.add(
                    OppgaveFeltverdiDto(
                        nøkkel = "aksjonspunkt",
                        verdi = null
                    )
                )
            }
        }

        // Sjekker også lukkede aksjonspunkter for å få med tidspunktet for oversendelser for historiske klagebehandlinger til dvh
        private fun utledTidspunktOversendtKabal(
            event: K9KlageEventDto,
            oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>
        ) {

            val oversendtKlageinstansKabalEllerBehandletIK9 = event.aksjonspunkttilstander
                .firstOrNull { apt -> apt.aksjonspunktKode == AksjonspunktDefinisjon.AUTO_OVERFØRT_NK.kode }
                ?: event.aksjonspunkttilstander
                    .firstOrNull { apt ->
                        apt.aksjonspunktKode == AksjonspunktDefinisjon.VURDERING_AV_FORMKRAV_KLAGE_KA.kode &&
                                setOf(AksjonspunktStatus.OPPRETTET, AksjonspunktStatus.UTFØRT).contains(apt.status)
                    }

            (oversendtKlageinstansKabalEllerBehandletIK9)?.let {
                oppgaveFeltverdiDtos.add(
                    OppgaveFeltverdiDto(
                        nøkkel = "oversendtKlageinstansTidspunkt",
                        verdi = it.opprettetTidspunkt?.toString()
                    )
                )
            }
        }

        private fun mapEnkeltverdier(
            event: K9KlageEventDto,
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
                nøkkel = "påklagdBehandlingUuid",
                verdi = event.påklagdBehandlingId?.toString(),
            ),
            OppgaveFeltverdiDto(
                nøkkel = "aktorId",
                verdi = event.aktørId
            ),
            OppgaveFeltverdiDto(
                nøkkel = "fagsystem",
                verdi = event.fagsystem.kode
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
                nøkkel = "relatertPartAktorid",
                verdi = event.relatertPartAktørId?.id
            ),
            OppgaveFeltverdiDto(
                nøkkel = "ansvarligSaksbehandler",
                verdi = event.ansvarligSaksbehandler ?: forrigeOppgave?.hentVerdi("ansvarligSaksbehandler")
            ),
            OppgaveFeltverdiDto(
                nøkkel = "ansvarligBeslutter",
                verdi = event.ansvarligBeslutter ?: forrigeOppgave?.hentVerdi("ansvarligBeslutter")
            ),
            OppgaveFeltverdiDto(
                nøkkel = "mottattDato",
                verdi = forrigeOppgave?.hentVerdi("mottattDato") ?: event.opprettetBehandling.toString()
            ),
            OppgaveFeltverdiDto(
                nøkkel = "registrertDato",
                verdi = forrigeOppgave?.hentVerdi("registrertDato") ?: event.opprettetBehandling.toString()
            ),
            OppgaveFeltverdiDto(
                nøkkel = "vedtaksdato",
                verdi = event.vedtaksdato?.toString() ?: forrigeOppgave?.hentVerdi("vedtaksdato")
            ),
            OppgaveFeltverdiDto(
                nøkkel = "totrinnskontroll",
                verdi = event.aksjonspunkttilstander.filter { aksjonspunktTilstandDto ->
                    aksjonspunktTilstandDto.aksjonspunktKode.equals("5015") && aksjonspunktTilstandDto.status.equals(
                        AksjonspunktStatus.AVBRUTT
                    ).not()
                }.isNotEmpty().toString()
            ),
            utledTidFørsteGangHosBeslutter(forrigeOppgave, event),
            OppgaveFeltverdiDto(
                nøkkel = "helautomatiskBehandlet",
                verdi = false.toString() //TODO: Påstand - klagesaker er alltid manuelt behandlet?
            )
        ).apply {
                if (event.pleietrengendeAktørId != null) {
                    add(
                        OppgaveFeltverdiDto(
                            nøkkel = "pleietrengendeAktorId",
                            verdi = event.pleietrengendeAktørId.toString()
                        )
                    )
                }
                add(
                    OppgaveFeltverdiDto(
                        nøkkel = "utenlandstilsnitt",
                        verdi = event.utenlandstilsnitt.toString()
                    )
                )
        }.filterNotNull().toMutableList()

        fun utledPåklagdBehandlingtype(
            event: K9KlageEventDto,
            oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>
        ) {

            event.påklagdBehandlingType?.let {
                oppgaveFeltverdiDtos.add(OppgaveFeltverdiDto(
                    nøkkel = "påklagdBehandlingtype",
                    verdi = it.toString()
                    )
                )
            }
        }

        @VisibleForTesting
        fun utledTidFørsteGangHosBeslutter(
            forrigeOppgave: OppgaveV3?,
            event: K9KlageEventDto
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

        private fun utledAvventerSaksbehandler(
            harManueltAksjonspunkt: Boolean,
            harAutopunkt: Boolean,
            oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>
        ) {
            if (harManueltAksjonspunkt && !harAutopunkt) {
                oppgaveFeltverdiDtos.add(
                    OppgaveFeltverdiDto(
                        nøkkel = "avventerSaksbehandler",
                        verdi = "true"
                    )
                )
            } else {
                oppgaveFeltverdiDtos.add(
                    OppgaveFeltverdiDto(
                        nøkkel = "avventerSaksbehandler",
                        verdi = "false"
                    )
                )
            }
        }

        private fun utledBehandlingsårsaker(
            event: K9KlageEventDto,
            oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>
        ) {
            val filtrert = event.behandlingsårsaker?.filterNot { behandlingsårsak ->
                behandlingsårsak == BehandlingÅrsakType.UDEFINERT.kode
            } ?: emptyList()
            if (filtrert.isNotEmpty()) {
                oppgaveFeltverdiDtos.addAll(filtrert.map { behandlingsårsak ->
                    OppgaveFeltverdiDto(
                        nøkkel = "behandlingsårsak",
                        verdi = KLAGE_PREFIX + behandlingsårsak
                    )
                })
            } else {
                oppgaveFeltverdiDtos.add(
                    OppgaveFeltverdiDto(
                        nøkkel = "behandlingsårsak",
                        verdi = null
                    )
                )
            }
        }
    }
}