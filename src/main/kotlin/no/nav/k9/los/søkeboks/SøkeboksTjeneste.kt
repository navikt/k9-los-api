package no.nav.k9.los.søkeboks

import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.pdl.IPdlService
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.omraade.OmrådeAdapter
import no.nav.k9.los.oppgaveuthenting.omraade.OmrådeAdaptere
import no.nav.k9.los.oppgaveuthenting.omraade.Søkeord
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import no.nav.k9.los.oppgaveuthenting.query.QueryRequest
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDtoBuilder

/**
 * Felles søkelogikk for alle områder. Klassen kjenner ingen feltkoder — all tolkning av
 * søkeord og oppgaver skjer i områdets [OmrådeAdapter].
 */
class SøkeboksTjeneste(
    private val pdlService: IPdlService,
    private val pepClient: IPepClient,
    private val oppgaveSammendragDtoBuilder: OppgaveSammendragDtoBuilder,
    private val queryService: OppgaveQueryService,
    private val områdeAdaptere: OmrådeAdaptere,
) {
    suspend fun finnOppgaver(søkeord: String, område: Områder): Søkeresultat {
        val adapter = områdeAdaptere.adapterFor(område)
        val oppgaver = finnOppgaverFor(søkeord, område, adapter) ?: return Søkeresultat.IkkeTilgang
        return transformerTilSøkeresultat(oppgaver, adapter)
    }

    suspend fun finnOppgaverSammendrag(søkeord: String, område: Områder): SøkeresultatSammendrag {
        val adapter = områdeAdaptere.adapterFor(område)
        val oppgaver = finnOppgaverFor(søkeord, område, adapter) ?: return SøkeresultatSammendrag.IkkeTilgang
        return transformerTilSøkeresultatSammendrag(oppgaver, adapter)
    }

    /**
     * Slår opp oppgaver basert på hva søkeordet ser ut som. Returnerer null dersom
     * innlogget bruker ikke har tilgang til personen bak søkeordet.
     */
    private suspend fun finnOppgaverFor(
        søkeord: String,
        område: Områder,
        adapter: OmrådeAdapter,
    ): List<Oppgave>? {
        val klassifisert = klassifiser(søkeord) ?: return null
        val query = adapter.lagQuery(klassifisert) ?: return emptyList()
        return queryService.queryForOppgave(QueryRequest(oppgaveQuery = query, område = område))
    }

    /**
     * Utleder søkeordvariant fra lengden på input. 11 tegn antas å være fødselsnummer,
     * 9 tegn journalpostId, ellers saksnummer. Returnerer null ved manglende tilgang til
     * personen bak et fødselsnummer.
     */
    private suspend fun klassifiser(søkeord: String): Søkeord? = when (søkeord.length) {
        11 -> {
            val pdlRespons = pdlService.identifikator(søkeord)
            if (pdlRespons.ikkeTilgang) {
                null
            } else {
                val aktørIder = pdlRespons.aktorId?.data?.hentIdenter?.identer?.map { it.ident } ?: emptyList()
                Søkeord.Person(søkeord, aktørIder)
            }
        }

        9 -> Søkeord.Journalpost(søkeord)
        else -> Søkeord.Sak(søkeord)
    }

    private suspend fun transformerTilSøkeresultat(
        oppgaver: List<Oppgave>,
        adapter: OmrådeAdapter,
    ): Søkeresultat {
        if (oppgaver.isEmpty()) {
            return Søkeresultat.TomtResultat
        }

        val aktørId = adapter.aktørId(oppgaver.first()) ?: return Søkeresultat.TomtResultat

        val (ikkeTilgang, person) = pdlService.person(aktørId)

        if (ikkeTilgang || person == null) {
            return Søkeresultat.IkkeTilgang
        }

        val filtrertForTilgang = énOppgavePerSak(oppgaver, adapter).filter {
            pepClient.harTilgangTilOppgaveV3(it)
        }

        if (filtrertForTilgang.isEmpty()) {
            return Søkeresultat.IkkeTilgang
        }

        val synligeOppgaver = filtrertForTilgang.filter { adapter.erSynlig(it) }

        return Søkeresultat.MedResultat(
            person = SøkeresultatPersonDto(person),
            oppgaver = synligeOppgaver.map { oppgave ->
                SøkeresultatOppgaveDto(adapter.tilSammendrag(oppgave, person))
            }
        )
    }

    private suspend fun transformerTilSøkeresultatSammendrag(
        oppgaver: List<Oppgave>,
        adapter: OmrådeAdapter,
    ): SøkeresultatSammendrag {
        if (oppgaver.isEmpty()) return SøkeresultatSammendrag.TomtResultat

        val aktørId = adapter.aktørId(oppgaver.first()) ?: return SøkeresultatSammendrag.TomtResultat
        val (ikkeTilgang, person) = pdlService.person(aktørId)
        if (ikkeTilgang || person == null) return SøkeresultatSammendrag.IkkeTilgang

        val filtrertForTilgang = énOppgavePerSak(oppgaver, adapter).filter {
            pepClient.harTilgangTilOppgaveV3(it)
        }
        if (filtrertForTilgang.isEmpty()) return SøkeresultatSammendrag.IkkeTilgang

        val synligeOppgaver = filtrertForTilgang.filter { adapter.erSynlig(it) }
        return SøkeresultatSammendrag.MedResultat(
            oppgaver = oppgaveSammendragDtoBuilder.bygg(
                synligeOppgaver,
                alleredeHentedePersoner = mapOf(aktørId to person),
            ),
        )
    }

    /** Beholder den åpne oppgaven per sak, eller den første dersom alle er lukket. */
    private fun énOppgavePerSak(oppgaver: List<Oppgave>, adapter: OmrådeAdapter): List<Oppgave> {
        val (oppgaverMedSak, oppgaverUtenSak) = oppgaver.partition { adapter.sakId(it) != null }

        val filtrerteMedSak = oppgaverMedSak.groupBy { adapter.sakId(it)!! }.values.map { oppgaverISak ->
            oppgaverISak.find { it.status != Oppgavestatus.LUKKET }
                ?: oppgaverISak.first()
        }

        return oppgaverUtenSak + filtrerteMedSak
    }
}
