package no.nav.k9.los.domeneadaptere.k9.eventmottak

import no.nav.k9.los.domeneadaptere.k9.eventmottak.eventlager.EventLagret

/**
 * Sjekker om innkommende eventer er i feil rekkefølge ved å sammenligne dirty og non-dirty eventer.
 * Feil rekkefølge betyr at det finnes et dirty event som er eldre (på ekstern_versjon)
 * enn et eksisterende non-dirty event.
 */
class FeilRekkefølgeSjekker {
    /**
     * Sjekker om det finnes dirty eventer som er eldre enn eksisterende non-dirty eventer.
     * Det indikerer at meldinger har kommet i feil rekkefølge.
     *
     * @return true hvis feil rekkefølge er oppdaget, ellers false
     */
    fun sjekkFeilRekkefølge(
        alleEventer: List<EventLagret>,
    ): Boolean {
        if (alleEventer.isEmpty()) return false

        var harSettClean = false
        for (event in alleEventer.asReversed()) {
            if (!event.dirty) {
                harSettClean = true
            } else if (harSettClean) {
                return true
            }
        }

        return false
    }
}
