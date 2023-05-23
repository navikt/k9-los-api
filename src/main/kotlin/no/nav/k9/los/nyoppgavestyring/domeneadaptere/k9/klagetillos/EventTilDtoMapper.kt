package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9klagetillos

import no.nav.k9.klage.kodeverk.behandling.BehandlingStatus
import no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktType
import no.nav.k9.klage.kontrakt.behandling.oppgavetillos.Aksjonspunkttilstand
import no.nav.k9.klage.kontrakt.behandling.oppgavetillos.KlagebehandlingProsessHendelse
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdiDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3

class EventTilDtoMapper {
    companion object {
        private val MANUELLE_AKSJONSPUNKTER = AksjonspunktDefinisjon.values().filter { aksjonspunktDefinisjon ->
            aksjonspunktDefinisjon.aksjonspunktType == AksjonspunktType.MANUELL
        }.map { aksjonspunktDefinisjon -> aksjonspunktDefinisjon.kode }

        private val AUTOPUNKTER = AksjonspunktDefinisjon.values().filter { aksjonspunktDefinisjon ->
            aksjonspunktDefinisjon.aksjonspunktType == AksjonspunktType.AUTOPUNKT
        }.map { aksjonspunktDefinisjon -> aksjonspunktDefinisjon.kode }

        internal fun lagOppgaveDto(event: KlagebehandlingProsessHendelse, forrigeOppgave: OppgaveV3?) = OppgaveDto(
            id = event.eksternId.toString(),
            versjon = event.eventTid.toString(),
            område = "K9",
            kildeområde = "K9",
            type = "k9klage",
            status = if (event.aksjonspunkttilstander.any { aksjonspunktTilstandDto -> aksjonspunktTilstandDto.status.erÅpentAksjonspunkt() }) {
                if (oppgaveSkalHaVentestatus(event)) {
                    "VENTER"
                } else {
                    "AAPEN"
                }
            } else {
                if (event.behandlingStatus == BehandlingStatus.UTREDES.toString()) {
                    "AAPEN"
                } else {
                    "LUKKET"
                }
            },
            endretTidspunkt = event.eventTid,
            reservasjonsnøkkel = utledReservasjonsnøkkel(event),
            feltverdier = lagFeltverdier(event, forrigeOppgave)
        )

        private fun utledReservasjonsnøkkel(event: KlagebehandlingProsessHendelse): String {
            return when (FagsakYtelseType.fraKode(event.ytelseTypeKode)) {
                FagsakYtelseType.PLEIEPENGER_SYKT_BARN -> if (erTilBeslutter(event)) {
                    "K9_b_${event.ytelseTypeKode}_${event.pleietrengendeAktørId}_beslutter"
                } else {
                    "K9_b_${event.ytelseTypeKode}_${event.pleietrengendeAktørId}"
                }

                else -> if (erTilBeslutter(event)) {
                    "K9_b_${event.ytelseTypeKode}_${event.aktørId}_beslutter"
                } else {
                    "K9_b_${event.ytelseTypeKode}_${event.aktørId}"
                }
            }
        }

        private fun oppgaveSkalHaVentestatus(event: KlagebehandlingProsessHendelse): Boolean {
            val oppgaveFeltverdiDtos = mutableListOf<OppgaveFeltverdiDto>()
            val åpneAksjonspunkter = getåpneAksjonspunkter(event)

            val harAutopunkt = åpneAksjonspunkter.any { aksjonspunktTilstandDto ->
                AUTOPUNKTER.contains(aksjonspunktTilstandDto.aksjonspunktKode)
            }

            val harManueltAksjonspunkt = åpneAksjonspunkter.any { aksjonspunktTilstandDto ->
                MANUELLE_AKSJONSPUNKTER.contains(aksjonspunktTilstandDto.aksjonspunktKode)
            }

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
            forrigeOppgave: OppgaveV3?
        ): List<OppgaveFeltverdiDto> {
            val oppgaveFeltverdiDtos = mapEnkeltverdier(event, forrigeOppgave)

            val åpneAksjonspunkter = getåpneAksjonspunkter(event)

            val harAutopunkt = åpneAksjonspunkter.any { aksjonspunktTilstandDto ->
                AUTOPUNKTER.contains(aksjonspunktTilstandDto.aksjonspunktKode)
            }

            val harManueltAksjonspunkt = åpneAksjonspunkter.any { aksjonspunktTilstandDto ->
                MANUELLE_AKSJONSPUNKTER.contains(aksjonspunktTilstandDto.aksjonspunktKode)
            }

            utledAksjonspunkter(event, oppgaveFeltverdiDtos)
            utledÅpneAksjonspunkter(åpneAksjonspunkter, oppgaveFeltverdiDtos)
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
                            verdi = åpentAksjonspunkt.aksjonspunktKode
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

        private fun utledAksjonspunkter(
            event: KlagebehandlingProsessHendelse,
            oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>
        ) {
            if (event.aksjonspunkttilstander.isNotEmpty()) {
                oppgaveFeltverdiDtos.addAll(event.aksjonspunkttilstander.map { aksjonspunkttilstand ->
                    OppgaveFeltverdiDto(
                        nøkkel = "aksjonspunkt",
                        verdi = aksjonspunkttilstand.aksjonspunktKode
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
                verdi = event.resultatType ?: "IKKE_FASTSATT"
            ),
            OppgaveFeltverdiDto(
                nøkkel = "ytelsestype",
                verdi = event.ytelseTypeKode
            ),
            OppgaveFeltverdiDto(
                nøkkel = "behandlingsstatus",
                verdi = event.behandlingStatus ?: "UTRED"
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
                nøkkel = "pleietrengendeAktorId",
                verdi = event.pleietrengendeAktørId?.id
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
                verdi = forrigeOppgave?.hentVerdi("mottattDato") ?: event.eventTid.toString()
            ),
            OppgaveFeltverdiDto(
                nøkkel = "registrertDato",
                verdi = forrigeOppgave?.hentVerdi("registrertDato") ?: event.eventTid.toString()
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
            )
        )

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
    }
}