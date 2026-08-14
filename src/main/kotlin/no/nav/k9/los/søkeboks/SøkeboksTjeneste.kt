package no.nav.k9.los.søkeboks

import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.pdl.IPdlService
import no.nav.k9.los.infrastruktur.pdl.navn
import no.nav.k9.los.kodeverk.BehandlingStatus
import no.nav.k9.los.kodeverk.FagsakYtelseType
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import no.nav.k9.los.oppgaveuthenting.query.QueryRequest
import no.nav.k9.los.oppgaveuthenting.query.dto.query.EnkelOrderFelt
import no.nav.k9.los.oppgaveuthenting.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.oppgaveuthenting.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDtoBuilder
import java.time.LocalDateTime

class SøkeboksTjeneste(
    private val pdlService: IPdlService,
    private val pepClient: IPepClient,
    private val oppgaveSammendragDtoBuilder: OppgaveSammendragDtoBuilder,
    private val søkeboksQueryFactory: K9SøkeboksQueryFactory,
) {
    suspend fun finnOppgaver(søkeord: String, område: Områder): Søkeresultat {
        val oppgaver = finnOppgaverFor(søkeord, område) ?: return Søkeresultat.IkkeTilgang
        return transformerTilSøkeresultat(oppgaver)
    }

    suspend fun finnOppgaverSammendrag(søkeord: String, område: Områder): SøkeresultatSammendrag {
        val oppgaver = finnOppgaverFor(søkeord, område) ?: return SøkeresultatSammendrag.IkkeTilgang
        return transformerTilSøkeresultatSammendrag(oppgaver)
    }

    /**
     * Slår opp oppgaver basert på hva søkeordet ser ut som. Returnerer null dersom
     * innlogget bruker ikke har tilgang til personen bak søkeordet.
     */
    private fun finnOppgaverFor(søkeord: String, område: Områder): List<Oppgave>? = when (søkeord.length) {
        11 -> søkeboksQueryFactory.finnOppgaverForFnr(søkeord)
        9 -> søkeboksQueryFactory.finnOppgaverForJournalpostId(søkeord)
        else -> søkeboksQueryFactory.finnOppgaverForSaksnummer(søkeord)
    }

    private suspend fun transformerTilSøkeresultat(
        oppgaver: List<Oppgave>,
    ): Søkeresultat {
        if (oppgaver.isEmpty()) {
            return Søkeresultat.TomtResultat
        }

        val aktørId = oppgaver.first().hentVerdi("aktorId")
            ?: return Søkeresultat.TomtResultat

        val (ikkeTilgang, person) = pdlService.person(aktørId)

        if (ikkeTilgang || person == null) {
            return Søkeresultat.IkkeTilgang
        }

        val filtrerteBasertPåSaksnummer = filtrerOppgaverBasertPåSaksnummer(oppgaver)

        val filtrertForTilgang = filtrerteBasertPåSaksnummer.filter {
            pepClient.harTilgangTilOppgaveV3(it)
        }

        if (filtrertForTilgang.isEmpty()) {
            return Søkeresultat.IkkeTilgang
        }

        return Søkeresultat.MedResultat(
            person = SøkeresultatPersonDto(person),
            oppgaver = filtrertForTilgang.mapNotNull { oppgave ->
                transformerOppgave(oppgave, person.navn())
            }
        )
    }

    private suspend fun transformerTilSøkeresultatSammendrag(
        oppgaver: List<Oppgave>,
    ): SøkeresultatSammendrag {
        if (oppgaver.isEmpty()) return SøkeresultatSammendrag.TomtResultat

        val aktørId = oppgaver.first().hentVerdi("aktorId")
            ?: return SøkeresultatSammendrag.TomtResultat
        val (ikkeTilgang, person) = pdlService.person(aktørId)
        if (ikkeTilgang || person == null) return SøkeresultatSammendrag.IkkeTilgang

        val filtrertForTilgang = filtrerOppgaverBasertPåSaksnummer(oppgaver).filter {
            pepClient.harTilgangTilOppgaveV3(it)
        }
        if (filtrertForTilgang.isEmpty()) return SøkeresultatSammendrag.IkkeTilgang

        val synligeOppgaver = filtrertForTilgang.filter { it.hentVerdi("ytelsestype") != "OBSOLETE" }
        return SøkeresultatSammendrag.MedResultat(
            oppgaver = oppgaveSammendragDtoBuilder.bygg(
                synligeOppgaver,
                alleredeHentedePersoner = mapOf(aktørId to person),
            ),
        )
    }

    private fun filtrerOppgaverBasertPåSaksnummer(oppgaver: List<Oppgave>): List<Oppgave> {
        val (oppgaverMedSaksnummer, oppgaverUtenSaksnummer) =
            oppgaver.partition { it.hentVerdi("saksnummer") != null }

        val gruppertPåSaksnummer = oppgaverMedSaksnummer.groupBy { it.hentVerdi("saksnummer")!! }

        val filtrerteMedSaksnummer = gruppertPåSaksnummer.values.map { oppgaverISak ->
            // Finn den ene oppgaven som ikke er lukket (hvis den finnes)
            oppgaverISak.find { it.status != Oppgavestatus.LUKKET } ?: oppgaverISak.first()
        }

        return oppgaverUtenSaksnummer + filtrerteMedSaksnummer
    }

    private fun transformerOppgave(oppgave: Oppgave, navn: String): SøkeresultatOppgaveDto? {
        if (oppgave.hentVerdi("ytelsestype") == "OBSOLETE") {
            return null
        }
        return SøkeresultatOppgaveDto(
            navn = navn,
            oppgaveNøkkel = OppgaveNøkkelDto(oppgave),
            ytelsestype = oppgave.hentVerdi("ytelsestype")?.let { FagsakYtelseType.fraKode(it).navn }
                ?: FagsakYtelseType.UKJENT.navn,
            saksnummer = oppgave.hentVerdi("saksnummer"),
            hastesak = oppgave.hentVerdi("hastesak") == "true",
            journalpostId = oppgave.hentVerdi("journalpostId"),
            opprettetTidspunkt = oppgave.hentVerdi("registrertDato")?.let { dato -> LocalDateTime.parse(dato) },
            status = oppgave.hentVerdi("behandlingsstatus")
                ?.let { kode -> BehandlingStatus.fraKode(kode).navn }
                ?: oppgave.status.visningsnavn,
            oppgavebehandlingsUrl = oppgave.getOppgaveBehandlingsurl(),
            reservasjonsnøkkel = oppgave.reservasjonsnøkkel,
            fagsakÅr = oppgave.hentVerdi("fagsakÅr")?.toIntOrNull()
        )
    }
}
