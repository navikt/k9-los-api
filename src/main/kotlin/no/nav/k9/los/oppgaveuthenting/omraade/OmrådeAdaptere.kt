package no.nav.k9.los.oppgaveuthenting.omraade

import no.nav.k9.los.oppgavedefinisjon.omraade.Område
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.omraade.k9.K9OmrådeAdapter

/**
 * Velger [OmrådeAdapter] for et område.
 *
 * Rutingen gjøres med et uttømmende `when` framfor et oppslag i en map bygget av alle
 * registrerte adaptere. Et `when` uten else-gren feiler ved kompilering når [Områder]
 * utvides, mens et map-oppslag først feiler i runtime på første request for det nye
 * området. Samme mønster som PepClient.tilgangKlientFor.
 */
class OmrådeAdaptere(
    private val k9Adapter: K9OmrådeAdapter,
) {
    fun adapterFor(område: Områder): OmrådeAdapter = when (område) {
        Områder.K9 -> k9Adapter
        Områder.UNG -> throw NotImplementedError("Områdeadapter for UNG er ikke implementert ennå")
    }

    fun adapterFor(område: Område): OmrådeAdapter = adapterFor(Områder.fraEksternId(område.eksternId))

    fun adapterFor(oppgave: Oppgave): OmrådeAdapter = adapterFor(oppgave.oppgavetype.område)
}
