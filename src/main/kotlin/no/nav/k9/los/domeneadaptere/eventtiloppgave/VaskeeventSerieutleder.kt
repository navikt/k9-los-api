package no.nav.k9.los.domeneadaptere.eventtiloppgave

import no.nav.k9.los.domeneadaptere.eventlager.EventLagret
import no.nav.k9.los.domeneadaptere.eventlager.Oppgavetype
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus

sealed interface NummerertEventHandling {
    val eventnummer: Int
    val event: EventLagret
    val oppgavetype: Oppgavetype

    data class MapEventHandling(
        override val eventnummer: Int,
        override val event: EventLagret,
        override val oppgavetype: Oppgavetype,
    ) : NummerertEventHandling

    /** Lukker siste versjon av [oppgavetype] fordi [event] har flyttet behandlingen over til en annen oppgavetype. */
    data class LukkOppgavetype(
        override val eventnummer: Int,
        override val event: EventLagret,
        override val oppgavetype: Oppgavetype,
    ) : NummerertEventHandling
}

/**
 * Utleder stegene for eventserien, nummerert per oppgavetype:
 *  - en vaskeevent arver nummeret til forrige ordinære event, slik at oppgaveversjonene ikke forskyves.
 *  - når oppgavetypen skifter mellom to eventer, lukkes forrige oppgavetype (hvis den ikke allerede er lukket)
 *    før eventet mappes. Lukkingen teller som en ordinær versjon for oppgavetypen den lukker.
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

    var forrige: Pair<Oppgavetype, EventLagret>? = null
    return eventer.flatMap { event ->
        val oppgavetype = event.oppgavetypeKode()
        val lukking = forrige
            ?.takeIf { (forrigeOppgavetype, forrigeEvent) ->
                forrigeOppgavetype != oppgavetype && forrigeEvent.oppgavestatus() != Oppgavestatus.LUKKET
            }
            ?.let { (forrigeOppgavetype, _) ->
                NummerertEventHandling.LukkOppgavetype(nesteNummer(forrigeOppgavetype, false), event, forrigeOppgavetype)
            }
        val mapping = NummerertEventHandling.MapEventHandling(nesteNummer(oppgavetype, event.erVaskeevent), event, oppgavetype)
        forrige = Pair(oppgavetype, event)
        if (event.dirty) listOfNotNull(lukking, mapping) else emptyList()
    }
}
