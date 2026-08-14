package no.nav.k9.los.søkeboks

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.infrastruktur.pdl.PdlService
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import no.nav.k9.los.oppgaveuthenting.query.QueryRequest
import no.nav.k9.los.oppgaveuthenting.query.dto.query.EnkelOrderFelt
import no.nav.k9.los.oppgaveuthenting.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.oppgaveuthenting.query.mapping.EksternFeltverdiOperator

class K9SøkeboksQueryFactory(
    val pdlService: PdlService,
    val queryService: OppgaveQueryService
) : SøkeboksQueryFactory {
    override fun finnOppgaverForJournalpostId(søkeord: String): List<Oppgave>? {
        val query = OppgaveQuery(
            filtere = listOf(
                FeltverdiOppgavefilter(
                    område = "K9",
                    kode = "journalpostId",
                    operator = EksternFeltverdiOperator.EQUALS,
                    verdi = listOf(søkeord)
                )
            ), order = listOf(EnkelOrderFelt("K9", "mottattDato", false))
        )
        return queryService.queryForOppgave(QueryRequest(oppgaveQuery = query))
    }

    override fun finnOppgaverForSaksnummer(søkeord: String): List<Oppgave>? {
        val query = OppgaveQuery(
            filtere = listOf(
                FeltverdiOppgavefilter(
                    område = "K9",
                    kode = "saksnummer",
                    operator = EksternFeltverdiOperator.EQUALS,
                    verdi = listOf(søkeord.uppercase().replace("O", "o").replace("I", "i"))
                )
            ), order = listOf(EnkelOrderFelt("K9", "mottattDato", false))
        )
        return queryService.queryForOppgave(QueryRequest(oppgaveQuery = query))
    }

    override fun finnOppgaverForFnr(søkeord: String): List<Oppgave>? {
        val pdlResponse = runBlocking(Dispatchers.IO) { pdlService.identifikator(søkeord) }
        return if (pdlResponse.ikkeTilgang) {
            null
        } else {
            val aktørIder = pdlResponse.aktorId?.data?.hentIdenter?.identer?.map { it.ident } ?: emptyList()
            val query = OppgaveQuery(
                filtere = listOf(
                    FeltverdiOppgavefilter(
                        område = "K9",
                        kode = "aktorId",
                        operator = EksternFeltverdiOperator.IN,
                        verdi = aktørIder + søkeord
                    )
                ), order = listOf(EnkelOrderFelt("K9", "mottattDato", false))
            )
            queryService.queryForOppgave(QueryRequest(oppgaveQuery = query))
        }
    }
}