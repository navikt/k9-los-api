package no.nav.k9.los.domeneadaptere.eventtiloppgave

import no.nav.k9.los.domeneadaptere.eventlager.EventLagret
import no.nav.k9.los.domeneadaptere.eventlager.Oppgavetype

sealed interface NummerertEventHandling {
    val eventnummer: Int
    val event: EventLagret
    val oppgavetype: Oppgavetype

    data class MapEventHandling(
        override val eventnummer: Int,
        override val event: EventLagret,
        override val oppgavetype: Oppgavetype,
    ) : NummerertEventHandling
}

/**
 * Utleder stegene for eventserien, nummerert per oppgavetype:
 *  - en vaskeevent arver nummeret til forrige ordinære event, slik at oppgaveversjonene ikke forskyves.
 *  - skifte av oppgavetype krever at kildesystemet sender et eksplisitt event som lukker forrige oppgavetype.
 */
internal fun utledEventhandlinger(eventer: List<EventLagret>): List<NummerertEventHandling> {
    val antallPerOppgavetype = mutableMapOf<Oppgavetype, Int>()
    val antallVaskPerOppgavetype = mutableMapOf<Oppgavetype, Int>()

    fun nesteNummer(oppgavetype: Oppgavetype, erVaskeevent: Boolean): Int {
        val posisjon = antallPerOppgavetype.getOrDefault(oppgavetype, 0)
        antallPerOppgavetype[oppgavetype] = posisjon + 1
        if (erVaskeevent) {
            antallVaskPerOppgavetype[oppgavetype] = antallVaskPerOppgavetype.getOrDefault(oppgavetype, 0) + 1
        }
        return (posisjon - antallVaskPerOppgavetype.getOrDefault(oppgavetype, 0)).coerceAtLeast(0)
    }

    return eventer.mapNotNull { event ->
        val oppgavetype = event.oppgavetypeKode()
        val mapping = NummerertEventHandling.MapEventHandling(nesteNummer(oppgavetype, event.erVaskeevent), event, oppgavetype)
        mapping.takeIf { event.dirty }
    }
}
