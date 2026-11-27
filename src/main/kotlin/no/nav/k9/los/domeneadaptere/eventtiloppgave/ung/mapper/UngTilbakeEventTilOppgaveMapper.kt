package no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.mapper

import no.nav.k9.los.domeneadaptere.eventlager.EventLagret
import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.domeneadaptere.eventmottak.ung.tilbake.UngTilbakeEventDto
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.kodeverk.UngOppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.oppgavedefinisjon.UngdomsprogramytelsenFelter.Aksjonspunkt
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.oppgavedefinisjon.UngdomsprogramytelsenFelter.Behandling
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.oppgavedefinisjon.UngdomsprogramytelsenFelter.Sak
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.oppgavedefinisjon.UngdomsprogramytelsenFelter.Saksbehandling
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.oppgavedefinisjon.UngdomsprogramytelsenFelter.Tilbake
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.oppgavedefinisjon.UngdomsprogramytelsenFelter.Vedtak
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.oppgavedefinisjon.UngdomsprogramytelsenOppgavetyper
import no.nav.k9.los.domeneadaptere.eventmottak.ung.tilbake.TilbakeAksjonspunkt
import no.nav.k9.los.kodeverk.AksjonspunktStatus
import no.nav.k9.los.kodeverk.BehandlingStatus
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavedefinisjon.typesikker.Feltverdier
import no.nav.k9.los.oppgavedefinisjon.typesikker.hent
import no.nav.k9.los.oppgavemottak.NyOppgaveVersjonInnsending
import no.nav.k9.los.oppgavemottak.NyOppgaveversjon
import no.nav.k9.los.oppgavemottak.OppgaveDto
import no.nav.k9.los.oppgavemottak.OppgaveV3
import no.nav.k9.los.oppgavemottak.VaskOppgaveversjon
import no.nav.ung.kodeverk.behandling.BehandlingType
import no.nav.ung.kodeverk.behandling.BehandlingStatus as UngBehandlingStatus
import no.nav.ung.kodeverk.behandling.FagsakYtelseType

object UngTilbakeEventTilOppgaveMapper {
    fun utledReservasjonsnøkkel(event: EventLagret.UngTilbake, erTilBeslutter: Boolean): String {
        require(event.område == Områder.UNGDOMSPROGRAMYTELSEN) { "Kan kun mappe ungdomsprogramytelsen" }
        return "UPY_t_${event.eventDto.aktørId}" + if (erTilBeslutter) "_beslutter" else ""
    }

    fun lagOppgaveDto(eventLagret: EventLagret.UngTilbake, forrige: OppgaveV3?, eventnummer: Int): NyOppgaveVersjonInnsending {
        require(eventLagret.område == Områder.UNGDOMSPROGRAMYTELSEN) { "Kan kun mappe ungdomsprogramytelsen" }
        val event = eventLagret.eventDto
        require(event.fagsystem == Fagsystem.UNGTILBAKE.kode) { "Ugyldig fagsystem for UNGTILBAKE: ${event.fagsystem}" }
        require(BehandlingType.fraKode(event.behandlingTypeKode) in setOf(BehandlingType.TILBAKEKREVING, BehandlingType.REVURDERING_TILBAKEKREVING)) {
            "Ugyldig behandlingstype for UNGTILBAKE: ${event.behandlingTypeKode}"
        }
        val aksjonspunkter = event.aksjonspunktKoderMedStatusListe.map { (kode, status) ->
            AksjonspunktStatus.fraKode(status)
            TilbakeAksjonspunkt.fraKode(kode) to status
        }
        val åpne = aksjonspunkter.filter { it.second == AksjonspunktStatus.OPPRETTET.kode }.map { it.first }
        val tilBeslutter = TilbakeAksjonspunkt.FATTE_VEDTAK in åpne && TilbakeAksjonspunkt.FORESLA_VEDTAK !in åpne
        val verdier = Feltverdier(UngdomsprogramytelsenOppgavetyper.TILBAKE)
        verdier[Behandling.UUID] = event.eksternId.toString()
        verdier[Behandling.TYPEKODE] = BehandlingType.fraKode(event.behandlingTypeKode)
        verdier[Behandling.STATUS] = UngBehandlingStatus.fraKode(event.behandlingStatus)
        verdier[Behandling.STEG] = event.behandlingSteg
        verdier[Sak.AKTOR_ID] = event.aktørId
        verdier[Sak.FAGSYSTEM] = event.fagsystem
        verdier[Sak.SAKSNUMMER] = event.saksnummer
        verdier[Sak.MOTTATT_DATO] = forrige?.hent(Sak.MOTTATT_DATO) ?: event.opprettetBehandling
        verdier[Sak.REGISTRERT_DATO] = forrige?.hent(Sak.REGISTRERT_DATO) ?: event.opprettetBehandling
        verdier[Vedtak.YTELSESTYPE] = FagsakYtelseType.fraKode(event.ytelseTypeKode)
        verdier[Vedtak.BEHANDLENDE_ENHET] = event.behandlendeEnhet
        verdier[Saksbehandling.ANSVARLIG_SAKSBEHANDLER] =
            event.ansvarligSaksbehandlerIdent ?: forrige?.hent(Saksbehandling.ANSVARLIG_SAKSBEHANDLER)
        verdier[Saksbehandling.ANSVARLIG_BESLUTTER] =
            event.ansvarligBeslutterIdent ?: forrige?.hent(Saksbehandling.ANSVARLIG_BESLUTTER)
        verdier[Saksbehandling.LIGGER_HOS_BESLUTTER] = tilBeslutter
        verdier[Saksbehandling.TID_FORSTE_GANG_HOS_BESLUTTER] =
            forrige?.hent(Saksbehandling.TID_FORSTE_GANG_HOS_BESLUTTER) ?: event.eventTid.takeIf { tilBeslutter }
        verdier[Tilbake.FORSTE_FEILUTBETALING] =
            event.førsteFeilutbetaling ?: forrige?.hent(Tilbake.FORSTE_FEILUTBETALING)
        verdier[Tilbake.FEILUTBETALT_BELOP] =
            event.feilutbetaltBeløp?.toString() ?: forrige?.hent(Tilbake.FEILUTBETALT_BELOP)
        verdier[Tilbake.LOSBARE] = åpne.filterNot { it.erAutopunkt }.map { it.kode }
        verdier[Tilbake.UTFORTE] = aksjonspunkter.filter { it.second == AksjonspunktStatus.UTFØRT.kode }.map { it.first.kode }
        verdier[Tilbake.AVBRUTTE] = aksjonspunkter.filter { it.second == AksjonspunktStatus.AVBRUTT.kode }.map { it.first.kode }
        verdier[Aksjonspunkt.ALLE] = aksjonspunkter.map { it.first.feltkode }
        verdier[Aksjonspunkt.AKTIVE] = aksjonspunkter.filter { it.second == AksjonspunktStatus.OPPRETTET.kode }.map { it.first.feltkode }
        verdier[Aksjonspunkt.UTFORT] = aksjonspunkter.filter { it.second == AksjonspunktStatus.UTFØRT.kode }.map { it.first.feltkode }
        verdier[Aksjonspunkt.AVBRUTT] = aksjonspunkter.filter { it.second == AksjonspunktStatus.AVBRUTT.kode }.map { it.first.feltkode }
        verdier[Saksbehandling.HELAUTOMATISK] =
            aksjonspunkter.none { it.second == AksjonspunktStatus.UTFØRT.kode && !it.first.erAutopunkt }
        val oppgave = OppgaveDto(
            eksternId = event.eksternId.toString(),
            eksternVersjon = event.eventTid.toString(),
            type = UngOppgavetypenavn.UNGDOMSPROGRAMYTELSENTILBAKE,
            status = utledStatus(event, åpne),
            endretTidspunkt = event.eventTid,
            reservasjonsnøkkel = utledReservasjonsnøkkel(eventLagret, tilBeslutter),
            feltverdier = verdier.tilDto(),
        )
        return if (eventLagret.erVaskeevent) VaskOppgaveversjon(oppgave, eventnummer) else NyOppgaveversjon(oppgave)
    }

    private fun utledStatus(event: UngTilbakeEventDto, åpne: List<TilbakeAksjonspunkt>): Oppgavestatus =
        when (BehandlingStatus.fraKode(event.behandlingStatus)) {
            BehandlingStatus.OPPRETTET -> Oppgavestatus.UAVKLART
            BehandlingStatus.AVSLUTTET -> Oppgavestatus.LUKKET
            BehandlingStatus.UTREDES, BehandlingStatus.FATTER_VEDTAK, BehandlingStatus.IVERKSETTER_VEDTAK -> when {
                åpne.any { it.erAutopunkt } -> Oppgavestatus.VENTER
                åpne.isNotEmpty() -> Oppgavestatus.AAPEN
                else -> Oppgavestatus.UAVKLART
            }
            else -> throw IllegalArgumentException("Ugyldig behandlingsstatus for UNGTILBAKE: ${event.behandlingStatus}")
        }
}
