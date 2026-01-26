package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventLagret
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.KlageEventTilOppgaveMapper
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.SakEventTilOppgaveMapper

class VaskeeventSerieutleder(
    private val sakEventTilOppgaveMapper: SakEventTilOppgaveMapper,
    private val klageEventTilOppgaveMapper: KlageEventTilOppgaveMapper,
) {
    internal fun korrigerEventnummerForVaskeeventer(eventer: List<EventLagret>): List<Pair<Int, EventLagret>> {
        return when (eventer.first()) {
            is EventLagret.K9Sak -> {
                var antallVask = 0
                eventer.mapIndexed { index, lagret ->
                    if (sakEventTilOppgaveMapper.erVaskeevent(lagret as EventLagret.K9Sak)) {
                        antallVask++
                    }
                    if (index-antallVask < 0) {
                        Pair(0, lagret)
                    } else {
                        Pair(index - antallVask, lagret)
                    }
                }.filter { it.second.dirty }
            }
            is EventLagret.K9Klage -> {
                var antallVask = 0
                eventer.mapIndexed { index, lagret ->
                    if (klageEventTilOppgaveMapper.erVaskeevent(lagret as EventLagret.K9Klage)) {
                        antallVask++
                    }
                    if (index-antallVask < 0) {
                        Pair(0, lagret)
                    } else {
                        Pair(index - antallVask, lagret)
                    }
                }.filter { it.second.dirty }
            }
            else -> eventer.mapIndexed { index, lagret -> Pair(index, lagret) }.filter { it.second.dirty }
        }
    }
}