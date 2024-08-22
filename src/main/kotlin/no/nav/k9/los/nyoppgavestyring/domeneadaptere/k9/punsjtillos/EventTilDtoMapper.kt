package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.punsjtillos

import no.nav.k9.kodeverk.behandling.BehandlingStatus
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.*
import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.los.integrasjon.kafka.dto.PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdiDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto

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
                if (event.sendtInn == true) {
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
                event.type?.let {
                    OppgaveFeltverdiDto(
                        nøkkel = "behandlingTypekode",
                        verdi = BehandlingType.fraKode(it).kode,
                    )
                },
                event.ytelse?.let {
                    OppgaveFeltverdiDto(
                        nøkkel = "ytelsestype",
                        verdi = it,
                    )
                },
                event.ferdigstiltAv?.let {
                    OppgaveFeltverdiDto(
                        nøkkel = "ansvarligSaksbehandler",
                        verdi = it,
                    )
                },
                event.journalførtTidspunkt?.let {
                    OppgaveFeltverdiDto(
                        nøkkel = "journalfortTidspunkt",
                        verdi = it.toString(),
                    )
                },
                OppgaveFeltverdiDto(
                    nøkkel = "journalfort",
                    verdi = (event.journalførtTidspunkt != null).toString(),
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "registrertDato",
                    verdi = forrigeOppgave?.let { forrigeOppgave.hentVerdi("registrertDato") } ?: event.eventTid.toString(),
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "mottattDato",
                    verdi = forrigeOppgave?.let { forrigeOppgave.hentVerdi("mottattDato") } ?: event.eventTid.toString(),
                )
            )
        }

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
    }
}
