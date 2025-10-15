package no.nav.k9.los.nyoppgavestyring.søkeboks

import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.IPdlService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.navn
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelOrderFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper
import java.time.LocalDateTime

class SøkeboksTjeneste(
    private val queryService: OppgaveQueryService,
    private val oppgaveRepository: OppgaveRepositoryTxWrapper,
    private val pdlService: IPdlService,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val pepClient: IPepClient,
) {
    suspend fun finnOppgaver(søkeord: String): Søkeresultat {
        val oppgaver = when (søkeord.length) {
            11 -> {
                val pdlResponse = pdlService.identifikator(søkeord)
                if (pdlResponse.ikkeTilgang) return Søkeresultat.IkkeTilgang
                val aktørIder = pdlResponse.aktorId?.data?.hentIdenter?.identer?.map { it.ident } ?: emptyList()
                finnOppgaverForAktørId(aktørIder + søkeord)
            }

            9 -> {
                finnOppgaverForJournalpostId(søkeord)
            }

            else -> finnOppgaverForSaksnummer(søkeord)
        }
        return transformerTilSøkeresultat(oppgaver)
    }

    private fun finnOppgaverForJournalpostId(journalpostId: String): List<Oppgave> {
        val query = OppgaveQuery(
            filtere = listOf(
                FeltverdiOppgavefilter(
                    område = "K9",
                    kode = "journalpostId",
                    operator = EksternFeltverdiOperator.EQUALS,
                    verdi = listOf(journalpostId)
                )
            ), order = listOf(EnkelOrderFelt("K9", "mottattDato", false))
        )
        return queryService.queryForOppgave(QueryRequest(oppgaveQuery = query))
    }

    private suspend fun finnOppgaverForSøkersFnr(fnr: String): List<Oppgave> {
        val aktørId =
            pdlService.identifikator(fnr).aktorId?.data?.hentIdenter?.identer?.get(0)?.ident ?: return emptyList()
        val query = OppgaveQuery(
            filtere = listOf(
                FeltverdiOppgavefilter(
                    område = "K9",
                    kode = "aktorId",
                    operator = EksternFeltverdiOperator.IN,
                    verdi = listOf(aktørId, fnr)
                )
            ), order = listOf(EnkelOrderFelt("K9", "mottattDato", false))
        )
        return queryService.queryForOppgave(QueryRequest(oppgaveQuery = query))
    }

    private fun finnOppgaverForAktørId(aktørIder: List<String>): List<Oppgave> {
        val query = OppgaveQuery(
            filtere = listOf(
                FeltverdiOppgavefilter(
                    område = "K9",
                    kode = "aktorId",
                    operator = EksternFeltverdiOperator.IN,
                    verdi = aktørIder
                )
            ), order = listOf(EnkelOrderFelt("K9", "mottattDato", false))
        )
        return queryService.queryForOppgave(QueryRequest(oppgaveQuery = query))
    }

    private fun finnOppgaverForSaksnummer(saksnummer: String): List<Oppgave> {
        val query = OppgaveQuery(
            filtere = listOf(
                FeltverdiOppgavefilter(
                    område = "K9",
                    kode = "saksnummer",
                    operator = EksternFeltverdiOperator.EQUALS,
                    verdi = listOf(saksnummer.uppercase().replace("O", "o").replace("I", "i"))
                )
            ), order = listOf(EnkelOrderFelt("K9", "mottattDato", false))
        )
        return queryService.queryForOppgave(QueryRequest(oppgaveQuery = query))
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

    private fun filtrerOppgaverBasertPåSaksnummer(oppgaver: List<Oppgave>): List<Oppgave> {
        val (oppgaverMedSaksnummer, oppgaverUtenSaksnummer) =
            oppgaver.partition { it.hentVerdi("saksnummer") != null }

        val gruppertPåSaksnummer = oppgaverMedSaksnummer.groupBy { it.hentVerdi("saksnummer")!! }

        val filtrerteMedSaksnummer = gruppertPåSaksnummer.values.map { oppgaverISak ->
            // Finn den ene oppgaven som ikke er lukket (hvis den finnes)
            oppgaverISak.find { it.status != "LUKKET" } ?: oppgaverISak.first()
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
                ?: Oppgavestatus.fraKode(oppgave.status).visningsnavn,
            oppgavebehandlingsUrl = oppgave.getOppgaveBehandlingsurl(),
            reservasjonsnøkkel = oppgave.reservasjonsnøkkel,
            fagsakÅr = oppgave.hentVerdi("fagsakÅr")?.toIntOrNull()
        )
    }
}