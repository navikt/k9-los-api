package no.nav.k9.los.oppgaveuthenting.sammendrag

import no.nav.k9.los.infrastruktur.pdl.IPdlService
import no.nav.k9.los.infrastruktur.pdl.PersonPdl
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave

class OppgaveSammendragDtoBuilder(
    mappere: List<OppgaveSammendragMapper>,
    private val pdlService: IPdlService,
) {
    private val mappere = mappere.associateBy { it.område }

    suspend fun bygg(
        oppgaver: List<Oppgave>,
        alleredeHentedePersoner: Map<String, PersonPdl?> = emptyMap(),
    ): List<OppgaveSammendragDto> {
        val personer = alleredeHentedePersoner.toMutableMap()
        return oppgaver.map { oppgave ->
            val område = Områder.fraEksternId(oppgave.oppgavetype.område.eksternId)
            val mapper = mappere[område]
                ?: throw IllegalStateException("Mangler OppgaveSammendragMapper for område ${område.eksternId}")
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
