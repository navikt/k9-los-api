package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9klagetillos

import no.nav.k9.klage.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.klage.kodeverk.behandling.BehandlingStatus
import no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType
import no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktType
import no.nav.k9.klage.kontrakt.behandling.oppgavetillos.Aksjonspunkttilstand
import no.nav.k9.klage.kontrakt.behandling.oppgavetillos.KlagebehandlingProsessHendelse
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdiDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.LosOpplysningerSomManglerIKlageDto

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

        internal fun lagOppgaveDto(event: KlagebehandlingProsessHendelse, losOpplysningerSomManglerIKlageDto: LosOpplysningerSomManglerIKlageDto, forrigeOppgave: OppgaveV3?) = OppgaveDto(
            id = event.eksternId.toString(),
            versjon = event.eventTid.toString(),
            område = "K9",
            kildeområde = "K9",
            type = "k9klage",
            status = if (event.aksjonspunkttilstander.any { aksjonspunktTilstandDto -> aksjonspunktTilstandDto.status.erÅpentAksjonspunkt() }) {
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
            },
            endretTidspunkt = event.eventTid,
            reservasjonsnøkkel = utledReservasjonsnøkkel(event, losOpplysningerSomManglerIKlageDto),
            feltverdier = lagFeltverdier(event, losOpplysningerSomManglerIKlageDto, forrigeOppgave)
        )

        private fun utledReservasjonsnøkkel(event: KlagebehandlingProsessHendelse, losOpplysningerSomManglerIKlageDto: LosOpplysningerSomManglerIKlageDto): String {
            return when (FagsakYtelseType.fraKode(event.ytelseTypeKode)) {
                FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
                FagsakYtelseType.PLEIEPENGER_NÆRSTÅENDE,
                FagsakYtelseType.OMSORGSPENGER_KS,
                FagsakYtelseType.OMSORGSPENGER_AO,
                FagsakYtelseType.OPPLÆRINGSPENGER  -> if (erTilBeslutter(event)) {
                    "K9_k_${event.ytelseTypeKode}_${losOpplysningerSomManglerIKlageDto.pleietrengendeAktørId}_beslutter"
                } else {
                    "K9_k_${event.ytelseTypeKode}_${losOpplysningerSomManglerIKlageDto.pleietrengendeAktørId}"
                }

                else -> if (erTilBeslutter(event)) {
                    "K9_k_${event.ytelseTypeKode}_${event.aktørId}_beslutter"
                } else {
                    "K9_k_${event.ytelseTypeKode}_${event.aktørId}"
                }
            }
        }

        private fun oppgaveSkalHaVentestatus(event: KlagebehandlingProsessHendelse): Boolean {
            val oppgaveFeltverdiDtos = mutableListOf<OppgaveFeltverdiDto>()
            val åpneAksjonspunkter = getåpneAksjonspunkter(event)

            val harAutopunkt = åpneAksjonspunkter.any { aksjonspunktTilstandDto ->
                AUTOPUNKTER.contains(aksjonspunktTilstandDto.aksjonspunktKode)
            }

            val harManueltAksjonspunkt = event.aksjonspunkttilstander
                .filter { aksjonspunkttilstand -> aksjonspunkttilstand.status != AksjonspunktStatus.AVBRUTT  }
                .any { aksjonspunktTilstandDto -> MANUELLE_AKSJONSPUNKTER.contains(aksjonspunktTilstandDto.aksjonspunktKode) }

            utledAvventerSaksbehandler(
                harManueltAksjonspunkt = harManueltAksjonspunkt,
                harAutopunkt = harAutopunkt,
                oppgaveFeltverdiDtos = oppgaveFeltverdiDtos
            )

            return oppgaveFeltverdiDtos.first().nøkkel == "false"
        }

        private fun erTilBeslutter(event: KlagebehandlingProsessHendelse): Boolean {
            return getåpneAksjonspunkter(event).firstOrNull { ap ->
                ap.aksjonspunktKode.equals(AksjonspunktDefinisjon.FATTER_VEDTAK.kode)
            } != null
        }

        private fun lagFeltverdier(
            event: KlagebehandlingProsessHendelse,
            losOpplysningerSomManglerIKlageDto: LosOpplysningerSomManglerIKlageDto,
            forrigeOppgave: OppgaveV3?
        ): List<OppgaveFeltverdiDto> {
            val oppgaveFeltverdiDtos = mapEnkeltverdier(event, losOpplysningerSomManglerIKlageDto, forrigeOppgave)

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

            //automatiskBehandletFlagg er defaultet til False p.t.
            utledAvventerSaksbehandler(harManueltAksjonspunkt, harAutopunkt, oppgaveFeltverdiDtos)

            return oppgaveFeltverdiDtos
        }

        private fun getåpneAksjonspunkter(event: KlagebehandlingProsessHendelse) =
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
            if(behandlingSteg != null) {
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
            event: KlagebehandlingProsessHendelse,
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

        private fun mapEnkeltverdier(
            event: KlagebehandlingProsessHendelse,
            losOpplysningerSomManglerIKlageDto: LosOpplysningerSomManglerIKlageDto,
            forrigeOppgave: OppgaveV3?
        ) = mutableListOf(
            OppgaveFeltverdiDto(
                nøkkel = "behandlingUuid",
                verdi = event.eksternId.toString()
            ),
            OppgaveFeltverdiDto(
                nøkkel = "påklagdBehandlingUuid",
                verdi = event.påklagdBehandlingEksternId?.toString(),
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
            losOpplysningerSomManglerIKlageDto.pleietrengendeAktørId?.let {
                OppgaveFeltverdiDto(
                    nøkkel = "pleietrengendeAktorId",
                    verdi = losOpplysningerSomManglerIKlageDto.pleietrengendeAktørId.aktørId
                )
            },
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
            OppgaveFeltverdiDto(
                nøkkel = "helautomatiskBehandlet",
                verdi = false.toString() //TODO: Påstand - klagesaker er alltid manuelt behandlet?
            ),
            OppgaveFeltverdiDto(
                nøkkel = "utenlandstilsnitt",
                verdi = losOpplysningerSomManglerIKlageDto.isUtenlandstilsnitt.toString()
            )
        ).filterNotNull().toMutableList()

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

        //TODO Legge inn behandlingsårsak i event fra klage
        /*
        private fun utledBehandlingsårsaker(
            event: KlagebehandlingProsessHendelse,
            oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>
        ) {
            val filtrert = event.behandlingsårsaker.filterNot { behandlingsårsak ->
                behandlingsårsak == BehandlingÅrsakType.UDEFINERT
            }
            if (filtrert.isNotEmpty()) {
                oppgaveFeltverdiDtos.addAll(filtrert.map { behandlingsårsak ->
                    OppgaveFeltverdiDto(
                        nøkkel = "behandlingsårsak",
                        verdi = KLAGE_PREFIX +  behandlingsårsak.kode
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

         */

    }
}