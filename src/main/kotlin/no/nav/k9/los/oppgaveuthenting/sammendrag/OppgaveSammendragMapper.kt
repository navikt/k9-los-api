package no.nav.k9.los.oppgaveuthenting.sammendrag

import no.nav.k9.los.infrastruktur.pdl.PersonPdl
import no.nav.k9.los.oppgaveuthenting.Oppgave

/**
 * Mapper en oppgave til sammendragsformat for ett område.
 *
 * Implementasjonene eier feltkodene til sitt eget område og kan ikke gjenbrukes på tvers,
 * siden feltdefinisjonene er uavhengige per område. Hvilken implementasjon som gjelder
 * bestemmes av [OppgaveSammendragDtoBuilder.mapperFor].
 */
interface OppgaveSammendragMapper {
    fun map(oppgave: Oppgave, person: PersonPdl?): OppgaveSammendragDto
}
