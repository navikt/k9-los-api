package no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.mapper

import no.nav.k9.los.domeneadaptere.eventlager.EventLagret
import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.domeneadaptere.eventmottak.ung.tilbake.UngTilbakeEventDto
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.kodeverk.UngOppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventmottak.ung.tilbake.TilbakeAksjonspunkt
import no.nav.k9.los.kodeverk.AksjonspunktStatus
import no.nav.k9.los.kodeverk.BehandlingStatus
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavemottak.NyOppgaveVersjonInnsending
import no.nav.k9.los.oppgavemottak.NyOppgaveversjon
import no.nav.k9.los.oppgavemottak.OppgaveDto
import no.nav.k9.los.oppgavemottak.OppgaveFeltverdiDto
import no.nav.k9.los.oppgavemottak.OppgaveV3
import no.nav.k9.los.oppgavemottak.VaskOppgaveversjon
import no.nav.ung.kodeverk.behandling.BehandlingType

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
        val verdier = mutableListOf<OppgaveFeltverdiDto>()
        fun felt(nøkkel: String, verdi: Any?) {
            verdier.add(OppgaveFeltverdiDto(nøkkel = nøkkel, verdi = verdi?.toString()))
        }
        fun liste(nøkkel: String, verdierForFelt: List<String>) {
            if (verdierForFelt.isEmpty()) felt(nøkkel, null) else verdierForFelt.forEach { felt(nøkkel, it) }
        }
        felt("behandlingUuid", event.eksternId)
        felt("behandlingTypekode", event.behandlingTypeKode)
        felt("behandlingsstatus", event.behandlingStatus)
        felt("behandlingssteg", event.behandlingSteg)
        felt("aktorId", event.aktørId)
        felt("fagsystem", event.fagsystem)
        felt("saksnummer", event.saksnummer)
        felt("mottattDato", forrige?.hentVerdi("mottattDato") ?: event.opprettetBehandling)
        felt("registrertDato", forrige?.hentVerdi("registrertDato") ?: event.opprettetBehandling)
        felt("ytelsestype", event.ytelseTypeKode)
        felt("behandlendeEnhet", event.behandlendeEnhet)
        felt("ansvarligSaksbehandler", event.ansvarligSaksbehandlerIdent ?: forrige?.hentVerdi("ansvarligSaksbehandler"))
        felt("ansvarligBeslutter", event.ansvarligBeslutterIdent ?: forrige?.hentVerdi("ansvarligBeslutter"))
        felt("liggerHosBeslutter", tilBeslutter)
        felt("tidFørsteGangHosBeslutter", forrige?.hentVerdi("tidFørsteGangHosBeslutter") ?: event.eventTid.takeIf { tilBeslutter })
        felt("førsteFeilutbetalingDato", event.førsteFeilutbetaling ?: forrige?.hentVerdi("førsteFeilutbetalingDato"))
        felt("feilutbetaltBeløp", event.feilutbetaltBeløp ?: forrige?.hentVerdi("feilutbetaltBeløp"))
        liste("tilbakeLøsbartAksjonspunkt", åpne.filterNot { it.erAutopunkt }.map { it.kode })
        liste("tilbakeUtførtAksjonspunkt", aksjonspunkter.filter { it.second == AksjonspunktStatus.UTFØRT.kode }.map { it.first.kode })
        liste("tilbakeAvbruttAksjonspunkt", aksjonspunkter.filter { it.second == AksjonspunktStatus.AVBRUTT.kode }.map { it.first.kode })
        liste("aksjonspunkt", aksjonspunkter.map { it.first.feltkode })
        liste("aktivtAksjonspunkt", aksjonspunkter.filter { it.second == AksjonspunktStatus.OPPRETTET.kode }.map { it.first.feltkode })
        liste("utførtAksjonspunkt", aksjonspunkter.filter { it.second == AksjonspunktStatus.UTFØRT.kode }.map { it.first.feltkode })
        liste("avbruttAksjonspunkt", aksjonspunkter.filter { it.second == AksjonspunktStatus.AVBRUTT.kode }.map { it.first.feltkode })
        felt("helautomatisk", aksjonspunkter.none { it.second == AksjonspunktStatus.UTFØRT.kode && !it.first.erAutopunkt })
        val oppgave = OppgaveDto(
            eksternId = event.eksternId.toString(),
            eksternVersjon = event.eventTid.toString(),
            type = UngOppgavetypenavn.UNGDOMSPROGRAMYTELSENTILBAKE,
            status = utledStatus(event, åpne),
            endretTidspunkt = event.eventTid,
            reservasjonsnøkkel = utledReservasjonsnøkkel(eventLagret, tilBeslutter),
            feltverdier = verdier,
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
