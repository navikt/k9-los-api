package no.nav.k9.los.nyoppgavestyring.søkeboks

import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper


//TODO erstatter fagSakApis på sikt
class SøkeboksTjeneste(
    private val queryService: OppgaveQueryService,
    private val oppgaveRepository: OppgaveRepositoryTxWrapper
) {
    fun finnOppgaverForSøkersAktørId(aktørId: String, antallPrPage: Int, pageNr: Int): List<Oppgave> {
        val query = OppgaveQuery(
            listOf(
                FeltverdiOppgavefilter(
                    område = "K9",
                    kode = "aktorId",
                    operator = "EQUALS",
                    verdi = listOf(aktørId)
                )
            )
        )
        val oppgaveEksternIder = queryService.queryForOppgaveEksternId(QueryRequest(query))
        return oppgaveRepository.hentOppgaverPaget(
            eksternoppgaveIder = oppgaveEksternIder,
            antallPrPage = antallPrPage,
            pageNr = pageNr+1
        )
    }
}