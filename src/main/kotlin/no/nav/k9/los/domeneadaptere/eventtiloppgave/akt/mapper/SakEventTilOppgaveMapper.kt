package no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.mapper

import no.nav.k9.los.domeneadaptere.eventlager.EventLagret
import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.domeneadaptere.eventmottak.ung.sak.UngSakEventDto
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.kodeverk.AktOppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerFelter.Aksjonspunkt
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerFelter.Behandling
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerFelter.Sak
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerFelter.Saksbehandling
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerFelter.Soknad
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerFelter.Vedtak
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerFelter.Ventetid
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerOppgavetyper
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
        if (FagsakYtelseType.fraKode(event.ytelseTypeKode) != FagsakYtelseType.AKTIVITETSPENGER) {
            throw IllegalArgumentException("Kan kun mappe AKTIVITETSPENGER ytelse til oppgave")
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


    fun utledOppgavetype(event: UngSakEventDto): AktOppgavetypenavn {
        val åpneAksjonspunkter = getåpneAksjonspunkter(event)

        return when (BehandlingType.fraKode(event.behandlingTypeKode)) {
            BehandlingType.FØRSTEGANGSSØKNAD,
            BehandlingType.REVURDERING ->
                if (åpneAksjonspunkter.any { ap ->
                        AksjonspunktDefinisjon.fraKode(ap.aksjonspunktKode()).aksjonspunktType.erNavKontorAksjonspunkt()
                    }) {
                    AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1
                } else {
                    AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL2
                }

            BehandlingType.KLAGE,
            BehandlingType.ANKE ->
                AktOppgavetypenavn.AKTIVITETSPENGERKLAGE

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
            BehandlingStatus.NAV_KONTOR_BESLUTTER_VILKÅR,
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

    fun utledReservasjonsnokkel(eventLagret: EventLagret.UngSak, tilBeslutter: Boolean): String {
        val event = eventLagret.eventDto
        val del = when (utledOppgavetype(event)) {
            AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1 -> "del1"
            AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL2 -> "del2"
            AktOppgavetypenavn.AKTIVITETSPENGERKLAGE -> throw NotImplementedError()
        }
        val behandlingtype = when (BehandlingType.fraKode(eventLagret.eventDto.behandlingTypeKode)) {
            BehandlingType.FØRSTEGANGSSØKNAD,
            BehandlingType.REVURDERING -> "b"

            BehandlingType.KLAGE,
            BehandlingType.ANKE -> "k"

            BehandlingType.TILBAKEKREVING,
            BehandlingType.REVURDERING_TILBAKEKREVING,
            BehandlingType.UDEFINERT -> throw IllegalStateException("Ugyldig behandlingstype for UNGSAK event: ${eventLagret.eventDto.behandlingTypeKode}")
        }
        return if (tilBeslutter) {
            "AKT_${behandlingtype}_${del}_${event.aktørId}_beslutter"
        } else {
            "AKT_${behandlingtype}_${del}_${event.aktørId}"
        }
    }

    private fun erTilBeslutter(event: UngSakEventDto): Boolean {
        return getåpneAksjonspunkter(event).firstOrNull { ap ->
            ap.aksjonspunktKode.equals(AksjonspunktDefinisjon.FATTER_VEDTAK.kode) ||
                    ap.aksjonspunktKode.equals(AksjonspunktDefinisjon.NAV_KONTOR_BESLUTTER_VILKÅR.kode)
        } != null
    }

    private fun getåpneAksjonspunkter(event: UngSakEventDto) =
        event.aksjonspunktTilstander.filter { aksjonspunktTilstand ->
            aksjonspunktTilstand.status.erÅpentAksjonspunkt()
        }

    private fun AksjonspunktTilstandDto.definisjon() = AksjonspunktDefinisjon.fraKode(aksjonspunktKode)

    private fun lagFeltverdier(
        event: UngSakEventDto,
        forrigeOppgave: OppgaveV3?,
        oppgavetype: AktOppgavetypenavn,
    ): List<OppgaveFeltverdiDto> {
        val verdier = Feltverdier(AktivitetspengerOppgavetyper.hent(oppgavetype))
        mapEnkeltverdier(event, forrigeOppgave, oppgavetype, verdier)
        utledAksjonspunkter(event, verdier)
        utledTotrinnskontroll(event, verdier)
        utledAktivVentetid(event, verdier)
        return verdier.tilDto()
    }

    private fun mapEnkeltverdier(
        event: UngSakEventDto,
        forrigeOppgave: OppgaveV3?,
        oppgavetype: AktOppgavetypenavn,
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
        verdier[Vedtak.BEHANDLENDE_ENHET] = when (utledOppgavetype(event)) {
            AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1 -> event.navKontorbehandlendeEnhet

            AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL2,
            AktOppgavetypenavn.AKTIVITETSPENGERKLAGE -> event.behandlendeEnhet
        }

        verdier[Saksbehandling.LIGGER_HOS_BESLUTTER] = erTilBeslutter(event)
        verdier[Saksbehandling.ANSVARLIG_BESLUTTER] = when (oppgavetype) {
            AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1 -> event.navKontorBeslutter

            AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL2,
            AktOppgavetypenavn.AKTIVITETSPENGERKLAGE -> event.ansvarligBeslutterForTotrinn
        }
        verdier[Saksbehandling.ANSVARLIG_SAKSBEHANDLER] = when (oppgavetype) {
            AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1 -> event.navKontorAnsvarligSaksbehandler

            AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL2,
            AktOppgavetypenavn.AKTIVITETSPENGERKLAGE -> event.ansvarligSaksbehandlerForTotrinn
        }
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

