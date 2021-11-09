package no.nav.k9.aksjonspunktbehandling

import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.modell.IModell

interface EventTeller {

    fun tellEvent(modell : IModell, oppgave: Oppgave)

}
