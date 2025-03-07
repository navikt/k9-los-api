package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak

import no.nav.k9.los.domene.lager.oppgave.Oppgave

interface EventTeller {

    fun tellEvent(modell : IModell, oppgave: Oppgave)

}
