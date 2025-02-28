package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.tilbaketillos

import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.BehandlingStatus
import no.nav.k9.los.aksjonspunktbehandling.AksjonspunktDefinisjonK9Tilbake
import no.nav.k9.los.domene.modell.AksjonspunktStatus.OPPRETTET
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventTilbakeDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.*
import java.time.temporal.ChronoUnit

class EventTilDtoMapper(
    private val oppgaveFeltVerdiUtledere: OppgaveFeltVerdiUtledere
) {
    fun lagOppgaveDto(event: BehandlingProsessEventTilbakeDto, forrigeOppgave: OppgaveV3?): OppgaveDto {
        val oppgavestatus =
            finnOppgavestatusFraAksjonspunkter(event.aksjonspunktKoderMedStatusListe)
        return OppgaveDto(
            id = event.eksternId.toString(),
            versjon = event.eventTid.toString(),
            område = "K9",
            kildeområde = "K9",
            type = "k9tilbake",
            status = oppgavestatus.kode,
            endretTidspunkt = event.eventTid,
            reservasjonsnøkkel = utledReservasjonsnøkkel(event, erTilBeslutter(event)),
            feltverdier = lagFeltverdier(event, oppgavestatus, forrigeOppgave)
        )
    }

    fun utledReservasjonsnøkkel(event: BehandlingProsessEventTilbakeDto, erTilBeslutter: Boolean): String {
        return lagNøkkelAktør(event, erTilBeslutter)
    }

    private fun finnOppgavestatusFraAksjonspunkter(aksjonspunktMedStatus: Map<String, String>): Oppgavestatus {
        val harÅpentAutopunkt =
            aksjonspunktMedStatus.any { it.value == OPPRETTET.kode && AksjonspunktDefinisjonK9Tilbake.fraKode(it.key).erAutopunkt }
        val harÅpentAksjonspunkt =
            aksjonspunktMedStatus.any { it.value == OPPRETTET.kode && !AksjonspunktDefinisjonK9Tilbake.fraKode(it.key).erAutopunkt }
        return if (harÅpentAutopunkt) {
            Oppgavestatus.VENTER
        } else if (harÅpentAksjonspunkt) {
            Oppgavestatus.AAPEN
        } else {
            Oppgavestatus.LUKKET
        }
    }

    private fun lagNøkkelAktør(event: BehandlingProsessEventTilbakeDto, tilBeslutter: Boolean): String {
        return if (tilBeslutter) {
            "K9_t_${event.ytelseTypeKode}_${event.aktørId}_beslutter"
        } else {
            "K9_t_${event.ytelseTypeKode}_${event.aktørId}"
        }
    }

    private fun erTilBeslutter(event: BehandlingProsessEventTilbakeDto): Boolean {
        return event.aksjonspunktKoderMedStatusListe.any {
            it.value == OPPRETTET.kode && AksjonspunktDefinisjonK9Tilbake.fraKode(it.key) == AksjonspunktDefinisjonK9Tilbake.FATTE_VEDTAK
        }

    }

    private fun lagFeltverdier(
        event: BehandlingProsessEventTilbakeDto,
        oppgavestatus: Oppgavestatus,
        forrigeOppgave: OppgaveV3?
    ): List<OppgaveFeltverdiDto> {
        val oppgaveFeltverdiDtos = mapEnkeltverdier(event, oppgavestatus, forrigeOppgave).toMutableList()

        utledAksjonspunkter(event, oppgaveFeltverdiDtos)
        utledAutomatiskBehandletFlagg(event, forrigeOppgave, oppgaveFeltverdiDtos)
        return oppgaveFeltverdiDtos
    }

    private fun mapEnkeltverdier(
        event: BehandlingProsessEventTilbakeDto,
        oppgavestatus: Oppgavestatus,
        forrigeOppgave: OppgaveV3?
    ) = listOfNotNull(
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
        OppgaveFeltverdiDto(
            nøkkel = "tidFørsteGangHosBeslutter",
            verdi = forrigeOppgave?.hentVerdi("tidFørsteGangHosBeslutter") ?: event.eventTid.toString()
        ).takeIf { erTilBeslutter(event) },
        oppgaveFeltVerdiUtledere.utledFerdigstiltTidspunkt(oppgavestatus, forrigeOppgave, event.eventTid),
        oppgaveFeltVerdiUtledere.utledFerdigstiltEnhet(
            oppgavestatus,
            forrigeOppgave,
            event.ansvarligSaksbehandlerIdent
        ),
    )

    private fun utledAutomatiskBehandletFlagg(
        event: BehandlingProsessEventTilbakeDto,
        forrigeOppgave: OppgaveV3?,
        oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>
    ) {
        val harManueltAksjonspunkt = event.aksjonspunktKoderMedStatusListe
            .filter { it.value == OPPRETTET.kode || it.value == no.nav.k9.los.domene.modell.AksjonspunktStatus.UTFØRT.kode }
            .map { AksjonspunktDefinisjonK9Tilbake.fraKode(it.key) }
            .filter { !it.erAutopunkt }
            .isNotEmpty()
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

    private fun utledAksjonspunkter(
        event: BehandlingProsessEventTilbakeDto,
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
