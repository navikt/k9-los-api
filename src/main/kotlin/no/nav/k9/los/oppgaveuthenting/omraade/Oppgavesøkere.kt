package no.nav.k9.los.oppgaveuthenting.omraade

import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRuter
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.omraade.k9.K9Oppgavesøk
import no.nav.k9.los.oppgaveuthenting.omraade.ung.UngOppgavesøk

/** Velger [Oppgavesøk] for et område. Se [OmrådeRuter] for hvorfor rutingen er et `when`. */
class Oppgavesøkere(
    k9: K9Oppgavesøk,
    ung: UngOppgavesøk,
) : OmrådeRuter<Oppgavesøk>(k9, ung)

/**
 * Ruter på området oppgaven tilhører.
 *
 * Ligger som utvidelsesfunksjon her framfor på [OmrådeRuter], slik at den generiske ruteren
 * ikke trenger å kjenne oppgavemodellen og kan brukes for konsepter som ikke handler om oppgaver.
 */
fun <T : Any> OmrådeRuter<T>.forOppgave(oppgave: Oppgave): T = forOmråde(oppgave.oppgavetype.område)
