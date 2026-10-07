package no.nav.k9.los.domeneadaptere.eventtiloppgave

import no.nav.k9.los.domeneadaptere.eventlager.EventLagret

object VaskeeventSerieutleder {
    /**
     * Nummererer eventserien slik at vaskeeventer ikke forskyver oppgaveversjonene:
     * en vaskeevent arver nummeret til forrige ordinære event.
     */
    internal fun nummererEventseriePerOppgavetype(eventer: List<EventLagret>): List<Pair<Int, EventLagret>> {
        val antallPerOppgavetype = mutableMapOf<String, Int>()
        val antallVaskPerOppgavetype = mutableMapOf<String, Int>()
        return eventer.map { lagret ->
            val oppgavetype = lagret.oppgavetypeKode()
            val posisjon = antallPerOppgavetype.getOrDefault(oppgavetype, 0)
            antallPerOppgavetype[oppgavetype] = posisjon + 1
            if (lagret.erVaskeevent) {
                antallVaskPerOppgavetype[oppgavetype] = antallVaskPerOppgavetype.getOrDefault(oppgavetype, 0) + 1
            }
            Pair((posisjon - antallVaskPerOppgavetype.getOrDefault(oppgavetype, 0)).coerceAtLeast(0), lagret)
        }.filter { it.second.dirty }
    }
}