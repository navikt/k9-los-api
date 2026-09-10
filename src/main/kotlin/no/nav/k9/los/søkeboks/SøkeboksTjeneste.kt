package no.nav.k9.los.søkeboks

import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstMedOmråde
import no.nav.k9.los.infrastruktur.pdl.IPdlService
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import no.nav.k9.los.oppgaveuthenting.query.QueryRequest
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDtoBuilder

/**
 * Felles søkelogikk for alle områder. Klassen kjenner ingen feltkoder — all tolkning av
 * søkeord og oppgaver skjer i områdets [Oppgavesøk].
 */
class SøkeboksTjeneste(
    private val pdlService: IPdlService,
    private val pepClient: IPepClient,
    private val oppgaveSammendragDtoBuilder: OppgaveSammendragDtoBuilder,
    private val queryService: OppgaveQueryService,
    private val oppgavesøkere: Oppgavesøkere,
) {
    suspend fun finnOppgaver(søkeord: String, område: Områder, brukerkontekst: BrukerkontekstMedOmråde): Søkeresultat {
        brukerkontekst.krevOmråde(område)
        if (!brukerkontekst.harBasisTilgang) return Søkeresultat.IkkeTilgang
        require(område == Områder.K9) { "Oppgavesøk er ikke implementert for området" }
        val adapter = oppgavesøkere.forOmråde(område)
        val oppgaver = finnOppgaverFor(søkeord, adapter, brukerkontekst) ?: return Søkeresultat.IkkeTilgang
        return transformerTilSøkeresultat(oppgaver, adapter, brukerkontekst)
    }

    suspend fun finnOppgaverSammendrag(søkeord: String, område: Områder, brukerkontekst: BrukerkontekstMedOmråde): SøkeresultatSammendrag {
        brukerkontekst.krevOmråde(område)
        if (!brukerkontekst.harBasisTilgang) return SøkeresultatSammendrag.IkkeTilgang
        require(område == Områder.K9) { "Oppgavesøk er ikke implementert for området" }
        val adapterForOmråde = oppgavesøkere.forOmråde(område)
        val oppgaver = finnOppgaverFor(søkeord, adapterForOmråde, brukerkontekst) ?: return SøkeresultatSammendrag.IkkeTilgang
        return transformerTilSøkeresultatSammendrag(oppgaver, adapterForOmråde, brukerkontekst)
    }

    /**
     * Slår opp oppgaver basert på hva søkeordet ser ut som. Returnerer null dersom
     * innlogget bruker ikke har tilgang til personen bak søkeordet.
     */
    private suspend fun finnOppgaverFor(
        søkeord: String,
        adapter: Oppgavesøk,
        brukerkontekst: BrukerkontekstMedOmråde,
    ): List<Oppgave>? {
        val klassifisertSøkeord = klassifiser(søkeord, brukerkontekst) ?: return null
        val query = adapter.lagQuery(klassifisertSøkeord) ?: return emptyList()
        return queryService.queryForOppgave(QueryRequest(
            oppgaveQuery = query,
            område = brukerkontekst.område,
            harTilgangTilKode6 = brukerkontekst.harTilgangTilKode6,
        ))
    }

    /**
     * Utleder søkeordvariant fra lengden på input. 11 tegn antas å være fødselsnummer,
     * 9 tegn journalpostId, ellers saksnummer. Returnerer null ved manglende tilgang til
     * personen bak et fødselsnummer.
     */
    private suspend fun klassifiser(søkeord: String, brukerkontekst: BrukerkontekstMedOmråde): Søkeord? = when (søkeord.length) {
        11 -> {
            val pdlRespons = pdlService.identifikator(søkeord, brukerkontekst)
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
        adapter: Oppgavesøk,
        brukerkontekst: BrukerkontekstMedOmråde,
    ): Søkeresultat {
        if (oppgaver.isEmpty()) {
            return Søkeresultat.TomtResultat
        }

        val filtrertForTilgang = énOppgavePerSak(oppgaver, adapter).filter {
            pepClient.harTilgangTilOppgaveV3(it, brukerkontekst)
        }

        if (filtrertForTilgang.isEmpty()) {
            return Søkeresultat.IkkeTilgang
        }

        val aktørId = adapter.aktørId(filtrertForTilgang.first()) ?: return Søkeresultat.TomtResultat
        val (ikkeTilgang, person) = pdlService.person(aktørId, brukerkontekst)
        if (ikkeTilgang || person == null) return Søkeresultat.IkkeTilgang

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
        adapter: Oppgavesøk,
        brukerkontekst: BrukerkontekstMedOmråde,
    ): SøkeresultatSammendrag {
        if (oppgaver.isEmpty()) return SøkeresultatSammendrag.TomtResultat

        val filtrertForTilgang = énOppgavePerSak(oppgaver, adapter).filter {
            pepClient.harTilgangTilOppgaveV3(it, brukerkontekst)
        }
        if (filtrertForTilgang.isEmpty()) return SøkeresultatSammendrag.IkkeTilgang

        val aktørId = adapter.aktørId(filtrertForTilgang.first()) ?: return SøkeresultatSammendrag.TomtResultat
        val (ikkeTilgang, person) = pdlService.person(aktørId, brukerkontekst)
        if (ikkeTilgang || person == null) return SøkeresultatSammendrag.IkkeTilgang

        val synligeOppgaver = filtrertForTilgang.filter { adapter.erSynlig(it) }
        return SøkeresultatSammendrag.MedResultat(
            oppgaver = oppgaveSammendragDtoBuilder.bygg(
                synligeOppgaver,
                brukerkontekst,
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
