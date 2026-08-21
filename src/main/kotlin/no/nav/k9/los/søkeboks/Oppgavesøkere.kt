package no.nav.k9.los.søkeboks

import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRuter
import no.nav.k9.los.søkeboks.k9.K9Oppgavesøk
import no.nav.k9.los.søkeboks.ung.UngOppgavesøk

class Oppgavesøkere(
    k9: K9Oppgavesøk,
    ung: UngOppgavesøk,
) : OmrådeRuter<Oppgavesøk>(k9, ung)
