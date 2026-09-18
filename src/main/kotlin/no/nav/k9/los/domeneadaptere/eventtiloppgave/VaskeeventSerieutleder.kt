package no.nav.k9.los.domeneadaptere.eventtiloppgave

import no.nav.k9.los.domeneadaptere.eventlager.EventLagret

object VaskeeventSerieutleder {
    /**
     * Nummererer eventserien slik at vaskeeventer ikke forskyver oppgaveversjonene:
     * en vaskeevent arver nummeret til forrige ordinære event.
     */
    internal fun korrigerEventnummerForVaskeeventer(eventer: List<EventLagret>): List<Pair<Int, EventLagret>> {
        var antallVask = 0
        return eventer.mapIndexed { index, lagret ->
            if (lagret.erVaskeevent) {
                antallVask++
            }
            Pair((index - antallVask).coerceAtLeast(0), lagret)
        }.filter { it.second.dirty }
    }
}