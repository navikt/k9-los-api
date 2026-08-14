package no.nav.k9.los.oppgaveuthenting.sammendrag

import no.nav.k9.los.infrastruktur.pdl.IPdlService
import no.nav.k9.los.infrastruktur.pdl.PersonPdl
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave

/**
 * Bygger sammendrags-DTO-er for oppgaver, uavhengig av hvilket område oppgavene hører til.
 *
 * Ruting til områdets [OppgaveSammendragMapper] skjer i [mapperFor] med et uttømmende `when`
 * framfor et oppslag i en map bygget av alle registrerte mappere. Grunnen er at `when` uten
 * else-gren feiler ved kompilering når [Områder] utvides, mens et map-oppslag først feiler i
 * runtime på første request for det nye området. Samme mønster som PepClient.tilgangKlientFor.
 */
class OppgaveSammendragDtoBuilder(
    private val k9Mapper: K9OppgaveSammendragMapper,
    private val pdlService: IPdlService,
) {
    private fun mapperFor(område: Områder): OppgaveSammendragMapper = when (område) {
        Områder.K9 -> k9Mapper
        Områder.UNG -> throw NotImplementedError("Oppgavesammendrag for område UNG er ikke implementert ennå")
    }

    suspend fun bygg(
        oppgaver: List<Oppgave>,
        alleredeHentedePersoner: Map<String, PersonPdl?> = emptyMap(),
    ): List<OppgaveSammendragDto> {
        val personer = alleredeHentedePersoner.toMutableMap()
        return oppgaver.map { oppgave ->
            val område = Områder.fraEksternId(oppgave.oppgavetype.område.eksternId)
            val mapper = mapperFor(område)
            val aktørId = oppgave.hentVerdi("aktorId")
            val person = aktørId?.let {
                if (personer.containsKey(it)) personer[it] else pdlService.person(it).person.also { person ->
                    personer[it] = person
                }
            }
            mapper.map(oppgave, person)
        }
    }
}
