package no.nav.k9.los.oppgaveuthenting.sammendrag

import no.nav.k9.los.infrastruktur.pdl.IPdlService
import no.nav.k9.los.infrastruktur.pdl.PersonPdl
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.søkeboks.Oppgavesøkere

/**
 * Bygger sammendrags-DTO-er for oppgaver på tvers av områder.
 *
 * All tolkning av oppgavens felt skjer i områdets Oppgavesøk-implementasjon, slik at denne klassen
 * ikke kjenner feltkodene til noe område.
 */
class OppgaveSammendragDtoBuilder(
    private val oppgavesøkere: Oppgavesøkere,
    private val pdlService: IPdlService,
) {
    suspend fun bygg(
        oppgaver: List<Oppgave>,
        alleredeHentedePersoner: Map<String, PersonPdl?> = emptyMap(),
    ): List<OppgaveSammendragDto> {
        val personer = alleredeHentedePersoner.toMutableMap()
        return oppgaver.map { oppgave ->
            val adapter = oppgavesøkere.forOmråde(oppgave.oppgavetype.område)
            val aktørId = adapter.aktørId(oppgave)
            val person = aktørId?.let {
                if (personer.containsKey(it)) personer[it] else pdlService.person(it).person.also { person ->
                    personer[it] = person
                }
            }
            adapter.tilSammendrag(oppgave, person)
        }
    }
}
