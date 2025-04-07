package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak

import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.repository.ReservasjonRepository
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.sakogbehandling.kontrakt.BehandlingAvsluttet
import no.nav.k9.los.integrasjon.sakogbehandling.kontrakt.BehandlingOpprettet
import no.nav.k9.statistikk.kontrakter.Behandling
import no.nav.k9.statistikk.kontrakter.Sak

interface IModell {
    fun starterSak(): Boolean
    fun erTom(): Boolean
    fun dvhSak(): Sak
    fun dvhBehandling(
        saksbehandlerRepository: SaksbehandlerRepository,
        reservasjonRepository: ReservasjonRepository
    ): Behandling

    fun sisteSaksNummer(): String

    fun oppgave(): Oppgave

    fun behandlingOpprettetSakOgBehandling(): BehandlingOpprettet
    fun behandlingAvsluttetSakOgBehandling(): BehandlingAvsluttet
   fun fikkEndretAksjonspunkt(): Boolean 
}
