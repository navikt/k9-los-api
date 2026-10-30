package no.nav.k9.los.søkeboks

import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.infrastruktur.pdl.IPdlService
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import no.nav.k9.los.oppgaveuthenting.query.QueryRequest
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDtoBuilder

class SøkeboksTjeneste(
    private val pdlService: IPdlService,
    private val pepClient: IPepClient,
    private val oppgaveSammendragDtoBuilder: OppgaveSammendragDtoBuilder,
    private val queryService: OppgaveQueryService,
    private val oppgavesøkere: Oppgavesøkere,
) {
    suspend fun finnOppgaverSammendrag(område: Områder, idToken: IIdToken, søkeord: String): SøkeresultatSammendrag {
        val adapterForOmråde = oppgavesøkere.forOmråde(område)
        val oppgaver = finnOppgaverFor(område, idToken, søkeord, adapterForOmråde)
            ?: return SøkeresultatSammendrag.IkkeTilgang
        return transformerTilSøkeresultatSammendrag(område, idToken, oppgaver, adapterForOmråde)
    }

    /**
     * Slår opp oppgaver basert på hva søkeordet ser ut som. Returnerer null dersom
     * innlogget bruker ikke har tilgang til personen bak søkeordet.
     */
    private suspend fun finnOppgaverFor(
        område: Områder,
        idToken: IIdToken,
        søkeord: String,
        adapter: Oppgavesøk,
    ): List<Oppgave>? {
        val klassifisertSøkeord = klassifiser(søkeord)

        // Sjekker tilgang på person før oppslag på oppgaver
        if (klassifisertSøkeord is Søkeord.Person) {
            if (klassifisertSøkeord.ikkeTilgangPdl) return null
            if (klassifisertSøkeord.aktørIder.isNotEmpty() && !pepClient.harTilgangTilPersoner(
                    område,
                    idToken,
                    klassifisertSøkeord.aktørIder
                )
            ) return null
        }

        val query = adapter.lagQuery(klassifisertSøkeord) ?: return emptyList()
        return queryService.queryForOppgave(QueryRequest(
            oppgaveQuery = query,
            område = område,
        ))
    }

    /**
     * Utleder søkeordvariant fra lengden på input
     */
    private suspend fun klassifiser(søkeord: String): Søkeord = when (søkeord.length) {
        11 -> {
            val pdlRespons = pdlService.identifikator(søkeord)
            val aktørIder = pdlRespons.aktorId?.data?.hentIdenter?.identer?.map { it.ident } ?: emptyList()
            Søkeord.Person(søkeord,pdlRespons.ikkeTilgang, aktørIder)
        }

        9 -> Søkeord.Journalpost(søkeord)
        else -> Søkeord.Sak(søkeord)
    }

    private suspend fun transformerTilSøkeresultatSammendrag(
        område: Områder,
        idToken: IIdToken,
        oppgaver: List<Oppgave>,
        adapter: Oppgavesøk,
    ): SøkeresultatSammendrag {
        if (oppgaver.isEmpty()) return SøkeresultatSammendrag.TomtResultat

        val filtrertForTilgang = énOppgavePerSak(oppgaver, adapter).filter {
            pepClient.harTilgangTilOppgaveV3(område, idToken, it)
        }
        if (filtrertForTilgang.isEmpty()) return SøkeresultatSammendrag.IkkeTilgang

        val aktørId = adapter.aktørId(filtrertForTilgang.first()) ?: return SøkeresultatSammendrag.TomtResultat
        val (ikkeTilgang, person) = pdlService.person(aktørId)
        if (ikkeTilgang || person == null) return SøkeresultatSammendrag.IkkeTilgang

        val synligeOppgaver = filtrertForTilgang.filter { adapter.erSynlig(it) }
        return SøkeresultatSammendrag.MedResultat(
            oppgaver = oppgaveSammendragDtoBuilder.bygg(
                synligeOppgaver,
                alleredeHentedePersoner = mapOf(aktørId to person),
            ),
        )
    }

    /** Beholder den åpne oppgaven per sak, eller den første dersom alle er lukket. */
    private fun énOppgavePerSak(oppgaver: List<Oppgave>, adapter: Oppgavesøk): List<Oppgave> {
        val (oppgaverMedSak, oppgaverUtenSak) = oppgaver.partition { adapter.saksnummer(it) != null }

        val filtrerteMedSak = oppgaverMedSak.groupBy { adapter.saksnummer(it)!! }.values.map { oppgaverISak ->
            oppgaverISak.find { it.status != Oppgavestatus.LUKKET }
                ?: oppgaverISak.first()
        }

        return oppgaverUtenSak + filtrerteMedSak
    }
}
