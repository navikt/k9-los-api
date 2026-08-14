package no.nav.k9.los.oppgaveuthenting.sammendrag

import no.nav.k9.los.infrastruktur.pdl.PersonPdl
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave

interface OppgaveSammendragMapper {
    val område: Områder

    fun map(oppgave: Oppgave, person: PersonPdl?): OppgaveSammendragDto
}
