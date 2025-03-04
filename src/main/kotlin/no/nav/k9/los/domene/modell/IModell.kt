package no.nav.k9.los.domene.modell

import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.integrasjon.sakogbehandling.kontrakt.BehandlingAvsluttet
import no.nav.k9.los.integrasjon.sakogbehandling.kontrakt.BehandlingOpprettet

interface IModell {
    fun starterSak(): Boolean
    fun erTom(): Boolean

    fun sisteSaksNummer(): String

    fun oppgave(): Oppgave

    fun behandlingOpprettetSakOgBehandling(): BehandlingOpprettet
    fun behandlingAvsluttetSakOgBehandling(): BehandlingAvsluttet
   fun fikkEndretAksjonspunkt(): Boolean 
}
