package no.nav.k9.los.aksjonspunktbehandling

import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.modell.IModell

interface EventTeller {

    fun tellEvent(modell : IModell, oppgave: Oppgave)

}
