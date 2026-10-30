package no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.mapper

import no.nav.k9.los.domeneadaptere.eventlager.EventLagret
import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.kodeverk.UpyOppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.oppgavedefinisjon.UngdomsprogramytelsenFelter.Aksjonspunkt
import no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.oppgavedefinisjon.UngdomsprogramytelsenFelter.Behandling
import no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.oppgavedefinisjon.UngdomsprogramytelsenFelter.Sak
import no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.oppgavedefinisjon.UngdomsprogramytelsenFelter.Saksbehandling
import no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.oppgavedefinisjon.UngdomsprogramytelsenFelter.Soknad
import no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.oppgavedefinisjon.UngdomsprogramytelsenFelter.Vedtak
import no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.oppgavedefinisjon.UngdomsprogramytelsenFelter.Ventetid
import no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.oppgavedefinisjon.UngdomsprogramytelsenOppgavetyper
import no.nav.k9.los.domeneadaptere.eventmottak.ung.sak.UngSakEventDto
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.typesikker.Feltverdier
import no.nav.k9.los.oppgavedefinisjon.typesikker.hent
import no.nav.k9.los.oppgavemottak.*
import no.nav.ung.kodeverk.behandling.BehandlingResultatType
import no.nav.ung.kodeverk.behandling.BehandlingStatus
import no.nav.ung.kodeverk.behandling.BehandlingType
import no.nav.ung.kodeverk.behandling.FagsakYtelseType
import no.nav.ung.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.ung.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.ung.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.ung.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto

object SakEventTilOppgaveMapper {
    fun lagOppgaveDto(
        eventLagret: EventLagret.UngSak,
        forrigeOppgave: OppgaveV3?,
        eventnummer: Int
    ): NyOppgaveVersjonInnsending {
        if (eventLagret.fagsystem != Fagsystem.UNGSAK) {
            throw IllegalArgumentException("Kan kun mappe UNGSAK event til oppgave")
        }
        val event = eventLagret.eventDto
        if (FagsakYtelseType.fraKode(event.ytelseTypeKode) != FagsakYtelseType.UNGDOMSYTELSE) {
            throw IllegalArgumentException("Kan kun mappe UNGDOMSYTELSE ytelse til oppgave")
        }

        val oppgavetype = utledOppgavetype(event)
        val oppgaveDto = OppgaveDto(
            eksternId = event.eksternId.toString(),
            eksternVersjon = event.eventTid.toString(),
            type = oppgavetype,
            status = utledOppgavestatus(event),
            endretTidspunkt = event.eventTid,
            reservasjonsnøkkel = utledReservasjonsnokkel(eventLagret, erTilBeslutter(event)),
            feltverdier = lagFeltverdier(event, forrigeOppgave, oppgavetype),
        )

        return if (eventLagret.erVaskeevent) {
            VaskOppgaveversjon(dto = oppgaveDto, eventNummer = eventnummer)
        } else {
            NyOppgaveversjon(oppgaveDto)
        }
    }


    fun utledOppgavetype(event: UngSakEventDto): UpyOppgavetypenavn {
        return when (BehandlingType.fraKode(event.behandlingTypeKode)) {
            BehandlingType.FØRSTEGANGSSØKNAD,
            BehandlingType.REVURDERING ->
                UpyOppgavetypenavn.UNGDOMSPROGRAMYTELSENORDINÆR

            BehandlingType.KLAGE,
            BehandlingType.ANKE ->
                UpyOppgavetypenavn.UNGDOMSPROGRAMYTELSENKLAGE

            BehandlingType.TILBAKEKREVING,
            BehandlingType.REVURDERING_TILBAKEKREVING,
            BehandlingType.UDEFINERT -> throw IllegalStateException("Ugyldig behandlingstype for UNGSAK event: ${event.behandlingTypeKode}")
        }
    }

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
            else -> throw IllegalStateException("Ugyldig behandlingsstatus for UNG event: ${event.behandlingStatus}")
        }
    }

    fun utledReservasjonsnokkel(eventLagret: EventLagret.UngSak, tilBeslutter: Boolean): String {
        val event = eventLagret.eventDto
        val prefiks = when (utledOppgavetype(event)) {
            UpyOppgavetypenavn.UNGDOMSPROGRAMYTELSENORDINÆR -> "UPY_b_del1"
            UpyOppgavetypenavn.UNGDOMSPROGRAMYTELSENKLAGE -> "UPY_k"
        }
        return if (tilBeslutter) {
            "${prefiks}_${event.aktørId}_beslutter"
        } else {
            "${prefiks}_${event.aktørId}"
        }
    }

    private fun erTilBeslutter(event: UngSakEventDto): Boolean {
        return getåpneAksjonspunkter(event).any { ap ->
            ap.aksjonspunktKode == AksjonspunktDefinisjon.FATTER_VEDTAK.kode ||
                    ap.aksjonspunktKode == AksjonspunktDefinisjon.NAV_KONTOR_BESLUTTER_VILKÅR.kode
        }
    }

    private fun getåpneAksjonspunkter(event: UngSakEventDto) =
        event.aksjonspunktTilstander.filter { aksjonspunktTilstand ->
            aksjonspunktTilstand.status.erÅpentAksjonspunkt()
        }

    private fun AksjonspunktTilstandDto.definisjon() = AksjonspunktDefinisjon.fraKode(aksjonspunktKode)

    private fun lagFeltverdier(
        event: UngSakEventDto,
        forrigeOppgave: OppgaveV3?,
        oppgavetype: UpyOppgavetypenavn,
    ): List<OppgaveFeltverdiDto> {
        val verdier = Feltverdier(UngdomsprogramytelsenOppgavetyper.hent(oppgavetype))
        mapEnkeltverdier(event, forrigeOppgave, verdier)
        utledAksjonspunkter(event, verdier)
        utledTotrinnskontroll(event, verdier)
        utledAktivVentetid(event, verdier)
        return verdier.tilDto()
    }

    private fun mapEnkeltverdier(
        event: UngSakEventDto,
        forrigeOppgave: OppgaveV3?,
        verdier: Feltverdier,
    ) {
        verdier[Behandling.UUID] = event.eksternId.toString()
        verdier[Behandling.TYPEKODE] = BehandlingType.fraKode(event.behandlingTypeKode)
        verdier[Behandling.STATUS] = BehandlingStatus.fraKode(event.behandlingStatus)
        verdier[Behandling.STEG] = event.behandlingSteg
        verdier[Behandling.ARSAK] = event.behandlingsårsaker
        verdier[Behandling.RESULTATTYPE] = event.resultatType ?: BehandlingResultatType.IKKE_FASTSATT.kode

        event.nyeKrav?.let { verdier[Soknad.NYE_KRAV] = it }

        verdier[Sak.AKTOR_ID] = event.aktørId
        verdier[Sak.FAGSYSTEM] = event.fagsystem.kode
        verdier[Sak.SAKSNUMMER] = event.saksnummer
        verdier[Sak.MOTTATT_DATO] = event.eldsteDatoMedEndringFraSøker
            ?: forrigeOppgave?.hent(Sak.MOTTATT_DATO)
            ?: forrigeOppgave?.hent(Sak.REGISTRERT_DATO)
            ?: event.opprettetBehandling
        verdier[Sak.REGISTRERT_DATO] = forrigeOppgave?.hent(Sak.REGISTRERT_DATO)
            ?: event.opprettetBehandling

        verdier[Vedtak.DATO] = event.vedtaksdato ?: forrigeOppgave?.hent(Vedtak.DATO)
        verdier[Vedtak.YTELSESTYPE] = FagsakYtelseType.fraKode(event.ytelseTypeKode)
        verdier[Vedtak.BEHANDLENDE_ENHET] = event.behandlendeEnhet

        verdier[Saksbehandling.LIGGER_HOS_BESLUTTER] = erTilBeslutter(event)
        verdier[Saksbehandling.ANSVARLIG_BESLUTTER] = event.ansvarligBeslutterForTotrinn
        verdier[Saksbehandling.ANSVARLIG_SAKSBEHANDLER] = event.ansvarligSaksbehandlerForTotrinn
        (forrigeOppgave?.hent(Saksbehandling.TID_FORSTE_GANG_HOS_BESLUTTER)
            ?: event.eventTid.takeIf { erTilBeslutter(event) })
            ?.let { verdier[Saksbehandling.TID_FORSTE_GANG_HOS_BESLUTTER] = it }
    }

    private fun utledAksjonspunkter(event: UngSakEventDto, verdier: Feltverdier) {
        val åpneAksjonspunkter = getåpneAksjonspunkter(event).map { it.definisjon() }

        verdier[Aksjonspunkt.ALLE] = event.aksjonspunktTilstander.map { it.definisjon() }
        verdier[Aksjonspunkt.AKTIVE] = åpneAksjonspunkter
        if (åpneAksjonspunkter.isNotEmpty()) {
            verdier[Aksjonspunkt.LOSBART] = event.behandlingSteg?.let { steg ->
                åpneAksjonspunkter.firstOrNull { !it.erAutopunkt() && it.behandlingSteg?.kode == steg }
            }
        }
        verdier[Aksjonspunkt.FREMTIDIG] = åpneAksjonspunkter.filter {
            !it.erAutopunkt() && (it.behandlingSteg == null || it.behandlingSteg.kode != event.behandlingSteg)
        }
        verdier[Aksjonspunkt.UTFORT] = event.aksjonspunktTilstander
            .filter { it.status == AksjonspunktStatus.UTFØRT }
            .map { it.definisjon() }
        verdier[Aksjonspunkt.AVBRUTT] = event.aksjonspunktTilstander
            .filter { it.status == AksjonspunktStatus.AVBRUTT }
            .map { it.definisjon() }
    }

    private fun utledTotrinnskontroll(event: UngSakEventDto, verdier: Feltverdier) {
        verdier[Vedtak.TOTRINNSKONTROLL] = event.aksjonspunktTilstander.any { aksjonspunktTilstandDto ->
            aksjonspunktTilstandDto.aksjonspunktKode.equals("5015") && aksjonspunktTilstandDto.status != AksjonspunktStatus.AVBRUTT
        }
    }

    private fun utledAktivVentetid(event: UngSakEventDto, verdier: Feltverdier) {
        val ventendeAksjonspunkt = getåpneAksjonspunkter(event)
            .filter { aksjonspunktTilstandDto ->
                aksjonspunktTilstandDto.venteårsak != null && aksjonspunktTilstandDto.venteårsak != Venteårsak.UDEFINERT &&
                        aksjonspunktTilstandDto.status == AksjonspunktStatus.OPPRETTET
            }
            .singleOrNull()

        verdier[Ventetid.AKTIV_ARSAK] = ventendeAksjonspunkt?.venteårsak
        verdier[Ventetid.AKTIV_FRIST] = ventendeAksjonspunkt?.fristTid
    }
}

