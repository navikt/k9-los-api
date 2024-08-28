package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos

import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.BehandlingStatus
import no.nav.k9.kodeverk.behandling.BehandlingÅrsakType
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.*
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.los.integrasjon.kafka.dto.EventHendelse
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdiDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import java.time.temporal.ChronoUnit

class EventTilDtoMapper {
    companion object {
        private val MANUELLE_AKSJONSPUNKTER = AksjonspunktDefinisjon.values().filter { aksjonspunktDefinisjon ->
            aksjonspunktDefinisjon.aksjonspunktType == AksjonspunktType.MANUELL
        }.map { aksjonspunktDefinisjon -> aksjonspunktDefinisjon.kode }

        private val AUTOPUNKTER = AksjonspunktDefinisjon.values().filter { aksjonspunktDefinisjon ->
            aksjonspunktDefinisjon.aksjonspunktType == AksjonspunktType.AUTOPUNKT
        }.map { aksjonspunktDefinisjon -> aksjonspunktDefinisjon.kode }

        fun lagOppgaveDto(event: BehandlingProsessEventDto, forrigeOppgave: OppgaveV3?) =
            OppgaveDto(
                id = event.eksternId.toString(),
                versjon = event.eventTid.toString(),
                område = "K9",
                kildeområde = "K9",
                type = "k9sak",
                status = if (event.aksjonspunktTilstander.any { aksjonspunktTilstandDto -> aksjonspunktTilstandDto.status.erÅpentAksjonspunkt() }) {
                    if (oppgaveSkalHaVentestatus(event)) {
                        Oppgavestatus.VENTER.kode
                    } else {
                        Oppgavestatus.AAPEN.kode
                    }
                } else {
                    if (event.behandlingStatus != BehandlingStatus.AVSLUTTET.kode && event.behandlingStatus != BehandlingStatus.IVERKSETTER_VEDTAK.kode) {
                        Oppgavestatus.AAPEN.kode
                    } else {
                        Oppgavestatus.LUKKET.kode
                    }
                },
                endretTidspunkt = event.eventTid,
                reservasjonsnøkkel = utledReservasjonsnøkkel(event, erTilBeslutter(event)),
                feltverdier = lagFeltverdier(event, forrigeOppgave)
            )

        fun utledReservasjonsnøkkel(event: BehandlingProsessEventDto, erTilBeslutter: Boolean): String {
            return when (FagsakYtelseType.fraKode(event.ytelseTypeKode)) {
                FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
                FagsakYtelseType.PLEIEPENGER_NÆRSTÅENDE,
                FagsakYtelseType.OMSORGSPENGER_KS,
                FagsakYtelseType.OMSORGSPENGER_AO,
                FagsakYtelseType.OPPLÆRINGSPENGER -> lagNøkkelPleietrengendeAktør(event, erTilBeslutter)
                else -> lagNøkkelAktør(event, erTilBeslutter)
            }
        }

        private fun lagNøkkelPleietrengendeAktør(event: BehandlingProsessEventDto, tilBeslutter: Boolean): String {
            return if (tilBeslutter)
                "K9_b_${event.ytelseTypeKode}_${event.pleietrengendeAktørId}_beslutter"
            else {
                "K9_b_${event.ytelseTypeKode}_${event.pleietrengendeAktørId}"
            }
        }

        private fun lagNøkkelAktør(event: BehandlingProsessEventDto, tilBeslutter: Boolean): String {
            return if (tilBeslutter) {
                "K9_b_${event.ytelseTypeKode}_${event.aktørId}_beslutter"
            } else {
                "K9_b_${event.ytelseTypeKode}_${event.aktørId}"
            }
        }

        private fun erTilBeslutter(event: BehandlingProsessEventDto): Boolean {
            return getåpneAksjonspunkter(event).firstOrNull { ap ->
                ap.aksjonspunktKode.equals(AksjonspunktDefinisjon.FATTER_VEDTAK.kode)
            } != null
        }

        private fun oppgaveSkalHaVentestatus(event: BehandlingProsessEventDto): Boolean {
            val åpneAksjonspunkter = getåpneAksjonspunkter(event)

            val ventetype = utledVentetype(event.behandlingSteg, event.behandlingStatus, åpneAksjonspunkter)
            return ventetype != Ventekategori.AVVENTER_SAKSBEHANDLER
        }

        private fun lagFeltverdier(
            event: BehandlingProsessEventDto,
            forrigeOppgave: OppgaveV3?
        ): List<OppgaveFeltverdiDto> {
            val oppgaveFeltverdiDtos = mapEnkeltverdier(event, forrigeOppgave)

            val åpneAksjonspunkter = getåpneAksjonspunkter(event)

            val harEllerHarHattManueltAksjonspunkt = event.aksjonspunktTilstander
                .filter { aksjonspunktTilstandDto -> aksjonspunktTilstandDto.status != AksjonspunktStatus.AVBRUTT }
                .any { aksjonspunktTilstandDto -> MANUELLE_AKSJONSPUNKTER.contains(aksjonspunktTilstandDto.aksjonspunktKode) }

            utledAksjonspunkter(event, oppgaveFeltverdiDtos)
            utledÅpneAksjonspunkter(event.behandlingSteg, åpneAksjonspunkter, oppgaveFeltverdiDtos)
            utledVenteÅrsakOgFrist(åpneAksjonspunkter, oppgaveFeltverdiDtos)
            utledAutomatiskBehandletFlagg(forrigeOppgave, oppgaveFeltverdiDtos, harEllerHarHattManueltAksjonspunkt)
            utledSøknadsårsaker(event, oppgaveFeltverdiDtos)
            utledBehandlingsårsaker(event, oppgaveFeltverdiDtos)
            oppgaveFeltverdiDtos.addAll(
                ventekategoriTilFlagg(
                    utledVentetype(
                        event.behandlingSteg,
                        event.behandlingStatus,
                        åpneAksjonspunkter
                    )
                )
            )

            return oppgaveFeltverdiDtos
        }

        private fun getåpneAksjonspunkter(event: BehandlingProsessEventDto) =
            event.aksjonspunktTilstander.filter { aksjonspunktTilstand ->
                aksjonspunktTilstand.status.erÅpentAksjonspunkt()
            }

        private fun mapEnkeltverdier(
            event: BehandlingProsessEventDto,
            forrigeOppgave: OppgaveV3?
        ) = mutableListOf(
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
                verdi = event.relatertPartAktørId
            ),
            OppgaveFeltverdiDto(
                nøkkel = "pleietrengendeAktorId",
                verdi = event.pleietrengendeAktørId
            ),
            OppgaveFeltverdiDto(
                nøkkel = "ansvarligBeslutter",
                verdi = event.ansvarligBeslutterForTotrinn ?: forrigeOppgave?.hentVerdi("ansvarligBeslutter")
            ),
            OppgaveFeltverdiDto(
                nøkkel = "ansvarligSaksbehandler",
                verdi = event.ansvarligSaksbehandlerForTotrinn
                    ?: event.ansvarligSaksbehandlerIdent
                    ?: forrigeOppgave?.hentVerdi("ansvarligSaksbehandler")
            ),
            OppgaveFeltverdiDto(
                nøkkel = "mottattDato",
                verdi = event.eldsteDatoMedEndringFraSøker?.truncatedTo(ChronoUnit.SECONDS)?.toString() //TODO feltet heter *dato, avrunde til dato?
                    ?: forrigeOppgave?.hentVerdi("mottattDato")
                    ?: forrigeOppgave?.hentVerdi("registrertDato")
                    ?: event.opprettetBehandling.truncatedTo(ChronoUnit.SECONDS).toString() //TODO feltet heter *dato, avrunde til dato?
            ),
            OppgaveFeltverdiDto(
                nøkkel = "registrertDato",
                verdi = forrigeOppgave?.hentVerdi("registrertDato") ?: event.opprettetBehandling.truncatedTo(ChronoUnit.SECONDS) .toString() //TODO feltet heter *dato, avrunde til dato?
            ),
            OppgaveFeltverdiDto(
                nøkkel = "vedtaksdato",
                verdi = event.vedtaksdato?.toString() ?: forrigeOppgave?.hentVerdi("vedtaksdato")
            ),
            event.nyeKrav?.let {
                OppgaveFeltverdiDto(
                    nøkkel = "nyeKrav",
                    verdi = event.nyeKrav.toString()
                )
            },
            event.fraEndringsdialog?.let {
                OppgaveFeltverdiDto(
                    nøkkel = "fraEndringsdialog",
                    verdi = event.fraEndringsdialog.toString()
                )
            },
            OppgaveFeltverdiDto(
                nøkkel = "totrinnskontroll",
                verdi = event.aksjonspunktTilstander.filter { aksjonspunktTilstandDto ->
                    aksjonspunktTilstandDto.aksjonspunktKode.equals("5015") && aksjonspunktTilstandDto.status !in (listOf(
                        AksjonspunktStatus.AVBRUTT
                    ))
                }.isNotEmpty().toString()
            ),
            OppgaveFeltverdiDto(
                nøkkel = "utenlandstilsnitt",
                verdi = event.aksjonspunktTilstander
                    .filter { aksjonspunktTilstandDto ->
                        aksjonspunktTilstandDto.status != AksjonspunktStatus.AVBRUTT
                    }
                    .any { aksjonspunktTilstandDto ->
                        aksjonspunktTilstandDto.aksjonspunktKode == AksjonspunktKodeDefinisjon.AUTOMATISK_MARKERING_AV_UTENLANDSSAK_KODE
                            || aksjonspunktTilstandDto.aksjonspunktKode == AksjonspunktKodeDefinisjon.MANUELL_MARKERING_AV_UTLAND_SAKSTYPE_KODE
                    }.toString()
            ),
            if (forrigeOppgave?.hentVerdi("hastesak") == "true" && event.eventHendelse != EventHendelse.HASTESAK_MERKNAD_FJERNET || event.eventHendelse == EventHendelse.HASTESAK_MERKNAD_NY ) {
                OppgaveFeltverdiDto(
                    nøkkel = "hastesak",
                    verdi = "true"
                )
            } else {
                null //ikke hastesak
            }
        ).filterNotNull().toMutableList()

        internal fun utledVentetype(
            behandlingSteg: String?,
            behandlingStatus: String?,
            åpneAksjonspunkter: List<AksjonspunktTilstandDto>
        ): Ventekategori? {
            if (behandlingStatus == BehandlingStatus.AVSLUTTET.kode) {
                return null
            }

            if (åpneAksjonspunkter.isEmpty()) {
                return Ventekategori.AVVENTER_ANNET
            }

            val førsteAPMedFristOgVenteårsak = åpneAksjonspunkter
                .filter { aksjonspunktTilstandDto -> aksjonspunktTilstandDto.fristTid != null && aksjonspunktTilstandDto.venteårsak != null }
                .sortedBy { aksjonspunktTilstandDto -> aksjonspunktTilstandDto.fristTid }
                .firstOrNull()

            if (førsteAPMedFristOgVenteårsak != null) {
                return førsteAPMedFristOgVenteårsak.venteårsak.ventekategori
            }

            val autopunkter = åpneAksjonspunkter.filter { åpentAksjonspunkt ->
                val aksjonspunktDefinisjon = AksjonspunktDefinisjon.fraKode(åpentAksjonspunkt.aksjonspunktKode)
                aksjonspunktDefinisjon.erAutopunkt()
            }

            val ventekategorierPrioritert = listOf(
                Ventekategori.AVVENTER_TEKNISK_FEIL,
                Ventekategori.AVVENTER_SAKSBEHANDLER,
                Ventekategori.AVVENTER_ANNET,
                Ventekategori.AVVENTER_ARBEIDSGIVER,
                Ventekategori.AVVENTER_SØKER,
                Ventekategori.AVVENTER_ANNET_IKKE_SAKSBEHANDLINGSTID
            )

            // Prioriter autopunkter fremfor andre aksjonspunkter
            ventekategorierPrioritert.forEach { ventekategori ->
                if (apInneholder(autopunkter, ventekategori)) {
                    return ventekategori
                }
            }

            val løsbareManuelleAksjonspunkter = åpneAksjonspunkter.filter { åpentAksjonspunkt ->
                val aksjonspunktDefinisjon = AksjonspunktDefinisjon.fraKode(åpentAksjonspunkt.aksjonspunktKode)
                aksjonspunktDefinisjon.behandlingSteg != null && aksjonspunktDefinisjon.behandlingSteg.kode == behandlingSteg
            }
            ventekategorierPrioritert.forEach { ventekategori ->
                if (apInneholder(løsbareManuelleAksjonspunkter, ventekategori)) {
                    return ventekategori
                }
            }

            return Ventekategori.AVVENTER_ANNET
        }

        private fun ventekategoriTilFlagg(
            ventekategori: Ventekategori?
        ): List<OppgaveFeltverdiDto> {
            if (ventekategori == null) {
                return avventerflagg("")
            }
            return when (ventekategori) {
                Ventekategori.AVVENTER_SØKER -> avventerflagg("avventerSøker")
                Ventekategori.AVVENTER_ARBEIDSGIVER -> avventerflagg("avventerArbeidsgiver")
                Ventekategori.AVVENTER_SAKSBEHANDLER -> avventerflagg("avventerSaksbehandler")
                Ventekategori.AVVENTER_TEKNISK_FEIL -> avventerflagg("avventerTekniskFeil")
                Ventekategori.AVVENTER_ANNET -> avventerflagg("avventerAnnet")
                Ventekategori.AVVENTER_ANNET_IKKE_SAKSBEHANDLINGSTID -> avventerflagg("avventerAnnetIkkeSaksbehandlingstid")
                else -> throw IllegalArgumentException("Ukjent ventekategori: ${ventekategori}")
            }
        }

        private fun apInneholder(
            løsbareAksjonspunkt: List<AksjonspunktTilstandDto>,
            ventekategori: Ventekategori
        ): Boolean {
            return løsbareAksjonspunkt.firstOrNull { aksjonspunktTilstandDto ->
                AksjonspunktDefinisjon.fraKode(aksjonspunktTilstandDto.aksjonspunktKode).defaultVentekategori == ventekategori
            } != null
        }

        private fun avventerflagg(skalSettesTrue: String): List<OppgaveFeltverdiDto> {
            val oppgavefelter = mutableListOf<OppgaveFeltverdiDto>()
            listOf(
                "avventerSøker",
                "avventerArbeidsgiver",
                "avventerSaksbehandler",
                "avventerTekniskFeil",
                "avventerAnnet",
                "avventerAnnetIkkeSaksbehandlingstid"
            ).forEach {
                if (skalSettesTrue == it) {
                    oppgavefelter.add(
                        OppgaveFeltverdiDto(
                            nøkkel = it,
                            verdi = true.toString()
                        )
                    )
                } else {
                    oppgavefelter.add(
                        OppgaveFeltverdiDto(
                            nøkkel = it,
                            verdi = false.toString()
                        )
                    )
                }
            }

            return oppgavefelter
        }

        private fun utledAutomatiskBehandletFlagg(
            forrigeOppgave: OppgaveV3?,
            oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>,
            harManueltAksjonspunkt: Boolean
        ) {
            if (forrigeOppgave != null && forrigeOppgave.hentVerdi("helautomatiskBehandlet").toBoolean().not()) {
                oppgaveFeltverdiDtos.add(
                    OppgaveFeltverdiDto(
                        nøkkel = "helautomatiskBehandlet",
                        verdi = false.toString()
                    )
                )
            } else {
                oppgaveFeltverdiDtos.add(
                    OppgaveFeltverdiDto(
                        nøkkel = "helautomatiskBehandlet",
                        verdi = if (harManueltAksjonspunkt) false.toString() else true.toString()
                    )
                )
            }
        }

        private fun utledÅpneAksjonspunkter(
            behandlingSteg: String?,
            åpneAksjonspunkter: List<AksjonspunktTilstandDto>,
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
                if (behandlingSteg != null) {
                    åpneAksjonspunkter.firstOrNull { åpentAksjonspunkt ->
                        val aksjonspunktDefinisjon = AksjonspunktDefinisjon.fraKode(åpentAksjonspunkt.aksjonspunktKode)
                        !aksjonspunktDefinisjon.erAutopunkt() && aksjonspunktDefinisjon.behandlingSteg != null && aksjonspunktDefinisjon.behandlingSteg.kode == behandlingSteg
                    }?.let {
                        oppgaveFeltverdiDtos.add(
                            OppgaveFeltverdiDto(
                                nøkkel = "løsbartAksjonspunkt",
                                verdi = it.aksjonspunktKode
                            )
                        )
                    }
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

        private fun utledVenteÅrsakOgFrist(
            åpneAksjonspunkter: List<AksjonspunktTilstandDto>,
            oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>
        ) {
            if (åpneAksjonspunkter.isNotEmpty()) {
                åpneAksjonspunkter
                    .filter { aksjonspunktTilstandDto ->
                        (aksjonspunktTilstandDto.venteårsak != Venteårsak.UDEFINERT &&
                                aksjonspunktTilstandDto.venteårsak != null)
                            && aksjonspunktTilstandDto.status == AksjonspunktStatus.OPPRETTET
                    }
                    .singleOrNull()?.let { aksjonspunktTilstandDto ->
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
        }

        private fun utledSøknadsårsaker(
            event: BehandlingProsessEventDto,
            oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>
        ) {
            if (event.søknadsårsaker.isNotEmpty()) {
                oppgaveFeltverdiDtos.addAll(event.søknadsårsaker.map { søknadsårsak ->
                    OppgaveFeltverdiDto(
                        nøkkel = "søknadsårsak",
                        verdi = søknadsårsak
                    )
                })
            } else {
                oppgaveFeltverdiDtos.add(
                    OppgaveFeltverdiDto(
                        nøkkel = "søknadsårsak",
                        verdi = null
                    )
                )
            }
        }

        private fun utledBehandlingsårsaker(
            event: BehandlingProsessEventDto,
            oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>
        ) {
            val filtrert = event.behandlingsårsaker.filterNot { behandlingsårsak ->
                behandlingsårsak == BehandlingÅrsakType.UDEFINERT.toString()
            }
            if (filtrert.isNotEmpty()) {
                oppgaveFeltverdiDtos.addAll(filtrert.map { behandlingsårsak ->
                    OppgaveFeltverdiDto(
                        nøkkel = "behandlingsårsak",
                        verdi = behandlingsårsak
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

        private fun utledAksjonspunkter(
            event: BehandlingProsessEventDto,
            oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>
        ) {
            if (event.aksjonspunktTilstander.isNotEmpty()) {
                oppgaveFeltverdiDtos.addAll(event.aksjonspunktTilstander.map { aksjonspunktTilstand ->
                    OppgaveFeltverdiDto(
                        nøkkel = "aksjonspunkt",
                        verdi = aksjonspunktTilstand.aksjonspunktKode
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
    }
}
